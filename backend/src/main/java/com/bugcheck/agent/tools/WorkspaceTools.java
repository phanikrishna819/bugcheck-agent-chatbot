package com.bugcheck.agent.tools;

import com.bugcheck.agent.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class WorkspaceTools {
    private static final int MAX_FILE_BYTES = 256_000;
    private static final int MAX_TOOL_OUTPUT = 24_000;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public WorkspaceTools(WorkspaceService workspaceService, ObjectMapper objectMapper) {
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> definitions() {
        return List.of(
                function("list_files", "List files under a registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "workspaceRoot", string("Registered workspace root."),
                                "path", string("Relative directory path. Use empty string for root."),
                                "limit", integer("Maximum number of files to return, default 200.")),
                        "required", List.of("workspaceRoot"))),
                function("read_file", "Read a UTF-8 text file from the registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of("workspaceRoot", string("Registered workspace root."), "path", string("Relative file path.")),
                        "required", List.of("workspaceRoot", "path"))),
                function("search_files", "Regex search text files in the registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "workspaceRoot", string("Registered workspace root."),
                                "regex", string("Java regex to search for."),
                                "limit", integer("Maximum matches, default 100.")),
                        "required", List.of("workspaceRoot", "regex"))),
                function("git_status", "Run git status --short in the registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of("workspaceRoot", string("Registered workspace root.")),
                        "required", List.of("workspaceRoot"))),
                function("git_diff", "Read git diff output in the registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of("workspaceRoot", string("Registered workspace root."), "baseRef", string("Optional base ref.")),
                        "required", List.of("workspaceRoot"))),
                function("run_command", "Run a safe command in the registered workspace. Dangerous git/destructive commands are blocked.", Map.of(
                        "type", "object",
                        "properties", Map.of("workspaceRoot", string("Registered workspace root."), "command", string("Command line.")),
                        "required", List.of("workspaceRoot", "command"))),
                function("apply_patch", "Edit a file by replacing exact oldText with newText. Requires allowEdits=true.", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "workspaceRoot", string("Registered workspace root."),
                                "path", string("Relative file path."),
                                "oldText", string("Exact existing text to replace. Empty only when createIfMissing is true."),
                                "newText", string("Replacement text."),
                                "createIfMissing", bool("Create the file if it does not exist.")),
                        "required", List.of("workspaceRoot", "path", "oldText", "newText"))),
                function("write_report", "Write a markdown report under .github/.audit-trails in the registered workspace.", Map.of(
                        "type", "object",
                        "properties", Map.of("workspaceRoot", string("Registered workspace root."), "fileName", string("Markdown file name."), "content", string("Markdown content.")),
                        "required", List.of("workspaceRoot", "fileName", "content")))
        );
    }

    public ToolResult execute(String name, String argumentsJson, boolean allowEdits) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            return switch (name) {
                case "list_files" -> listFiles(args);
                case "read_file" -> readFile(args);
                case "search_files" -> searchFiles(args);
                case "git_status" -> runGit(args, "git status --short");
                case "git_diff" -> gitDiff(args);
                case "run_command" -> runCommand(args);
                case "apply_patch" -> applyPatch(args, allowEdits);
                case "write_report" -> writeReport(args, allowEdits);
                default -> ToolResult.error("Unknown tool: " + name);
            };
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private ToolResult listFiles(JsonNode args) throws IOException {
        String workspace = text(args, "workspaceRoot");
        String relative = text(args, "path", "");
        int limit = intValue(args, "limit", 200);
        Path root = workspaceService.requireRegisteredRoot(workspace);
        Path start = workspaceService.resolveInside(workspace, relative);
        if (!Files.exists(start)) {
            return ToolResult.error("Path does not exist: " + relative);
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start, 8)) {
            stream.filter(path -> !path.equals(start))
                    .filter(this::notIgnored)
                    .limit(limit)
                    .forEach(path -> files.add(workspaceService.relativize(root, path) + (Files.isDirectory(path) ? "/" : "")));
        }
        return ToolResult.ok(String.join("\n", files));
    }

    private ToolResult readFile(JsonNode args) throws IOException {
        Path file = workspaceService.resolveInside(text(args, "workspaceRoot"), text(args, "path"));
        if (!Files.isRegularFile(file)) {
            return ToolResult.error("File not found: " + file);
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            return ToolResult.error("File is too large to read through this tool: " + Files.size(file) + " bytes");
        }
        return ToolResult.ok(Files.readString(file, StandardCharsets.UTF_8));
    }

    private ToolResult searchFiles(JsonNode args) throws IOException {
        String workspace = text(args, "workspaceRoot");
        Path root = workspaceService.requireRegisteredRoot(workspace);
        Pattern pattern = Pattern.compile(text(args, "regex"));
        int limit = intValue(args, "limit", 100);
        List<String> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root, 12)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(this::notIgnored).toList();
            for (Path file : files) {
                if (matches.size() >= limit || Files.size(file) > MAX_FILE_BYTES) {
                    continue;
                }
                List<String> lines;
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    continue;
                }
                for (int i = 0; i < lines.size() && matches.size() < limit; i++) {
                    if (pattern.matcher(lines.get(i)).find()) {
                        matches.add(workspaceService.relativize(root, file) + ":" + (i + 1) + ": " + lines.get(i));
                    }
                }
            }
        }
        return ToolResult.ok(String.join("\n", matches));
    }

    private ToolResult runGit(JsonNode args, String command) throws IOException, InterruptedException {
        return executeProcess(workspaceService.requireRegisteredRoot(text(args, "workspaceRoot")), command);
    }

    private ToolResult gitDiff(JsonNode args) throws IOException, InterruptedException {
        String baseRef = text(args, "baseRef", "");
        String command = baseRef.isBlank() ? "git diff -- ." : "git diff " + safeToken(baseRef) + " -- .";
        return executeProcess(workspaceService.requireRegisteredRoot(text(args, "workspaceRoot")), command);
    }

    private ToolResult runCommand(JsonNode args) throws IOException, InterruptedException {
        String command = text(args, "command");
        if (isDangerous(command)) {
            return ToolResult.error("Command blocked by safety policy: " + command);
        }
        return executeProcess(workspaceService.requireRegisteredRoot(text(args, "workspaceRoot")), command);
    }

    private ToolResult applyPatch(JsonNode args, boolean allowEdits) throws IOException {
        if (!allowEdits) {
            return ToolResult.error("Edits are disabled for this session. Enable allowEdits before calling apply_patch.");
        }
        Path file = workspaceService.resolveInside(text(args, "workspaceRoot"), text(args, "path"));
        String oldText = text(args, "oldText", "");
        String newText = text(args, "newText", "");
        boolean createIfMissing = boolValue(args, "createIfMissing", false);
        if (!Files.exists(file)) {
            if (!createIfMissing) {
                return ToolResult.error("File does not exist: " + file);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, newText, StandardCharsets.UTF_8);
            return ToolResult.ok("Created " + file);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (!content.contains(oldText)) {
            return ToolResult.error("oldText was not found in " + file);
        }
        Files.writeString(file, content.replace(oldText, newText), StandardCharsets.UTF_8);
        return ToolResult.ok("Updated " + file);
    }

    private ToolResult writeReport(JsonNode args, boolean allowEdits) throws IOException {
        if (!allowEdits) {
            return ToolResult.error("Edits are disabled for this session. Enable allowEdits before writing reports.");
        }
        String fileName = text(args, "fileName");
        if (!fileName.endsWith(".md") || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ToolResult.error("Report fileName must be a simple .md file name.");
        }
        Path dir = workspaceService.resolveInside(text(args, "workspaceRoot"), ".github/.audit-trails");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName).normalize();
        Files.writeString(file, text(args, "content"), StandardCharsets.UTF_8);
        return ToolResult.ok("Report written: " + file);
    }

    private ToolResult executeProcess(Path cwd, String command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (process.isAlive() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        if (process.isAlive()) {
            process.destroyForcibly();
            return ToolResult.error("Command timed out after 60 seconds: " + command);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String capped = output.length() > MAX_TOOL_OUTPUT ? output.substring(output.length() - MAX_TOOL_OUTPUT) : output;
        return process.exitValue() == 0 ? ToolResult.ok(capped) : ToolResult.error(capped);
    }

    private List<String> shellCommand(String command) {
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        return windows ? List.of("powershell", "-NoProfile", "-Command", command) : List.of("/bin/sh", "-lc", command);
    }

    private boolean notIgnored(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/.git/") && !normalized.contains("/node_modules/") && !normalized.contains("/target/") && !normalized.contains("/dist/") && !normalized.contains("/build/");
    }

    private boolean isDangerous(String command) {
        String lower = command.toLowerCase();
        return lower.matches(".*\\bgit\\s+(reset|clean|checkout|switch|commit|push|merge|rebase)\\b.*")
                || lower.matches(".*\\b(remove-item|rm|rmdir|del)\\b.*")
                || lower.contains("--force")
                || lower.contains(" -f ");
    }

    private String safeToken(String token) {
        if (!token.matches("[A-Za-z0-9_./:-]+")) {
            throw new IllegalArgumentException("Unsafe git ref: " + token);
        }
        return token;
    }

    private String text(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument: " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private boolean boolValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }

    private Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", parameters));
    }

    private Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }
}
