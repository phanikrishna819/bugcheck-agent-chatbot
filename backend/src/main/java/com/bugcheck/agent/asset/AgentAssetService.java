package com.bugcheck.agent.asset;

import com.bugcheck.agent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class AgentAssetService {
    private final AgentProperties properties;

    public AgentAssetService(AgentProperties properties) {
        this.properties = properties;
    }

    public String buildSystemPrompt() {
        Path root = assetsRoot();
        String agent = readIfExists(root.resolve("bugcheck.agent.md"));
        String skills = readSkills(root.resolve("skills"));
        return """
                You are Bugcheck Agent, an autonomous code-review and bug-fix agent exposed through a chatbot UI.

                Follow these hard safety rules:
                - Use only the tools provided by the backend.
                - Never edit files unless the session explicitly enables edits.
                - Never edit outside the registered workspace root.
                - Never run git reset, git clean, git checkout, git switch, git commit, git push, or destructive shell commands.
                - Prefer minimal, targeted changes.
                - Quote real files and lines when reporting findings.

                Main bugcheck agent file:
                ```markdown
                %s
                ```

                Skill files:
                %s
                """.formatted(agent, skills);
    }

    public List<String> listSkillNames() {
        Path skills = assetsRoot().resolve("skills");
        if (!Files.isDirectory(skills)) {
            return List.of();
        }
        try (var stream = Files.list(skills)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Path assetsRoot() {
        return Path.of(properties.assetsDir()).toAbsolutePath().normalize();
    }

    private String readSkills(Path skillsDir) {
        if (!Files.isDirectory(skillsDir)) {
            return "No skills directory found at " + skillsDir;
        }
        try (var stream = Files.list(skillsDir)) {
            StringBuilder builder = new StringBuilder();
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path file : files) {
                builder.append("\n## ").append(file.getFileName()).append("\n");
                builder.append("```markdown\n").append(readIfExists(file)).append("\n```\n");
            }
            return builder.toString();
        } catch (IOException e) {
            return "Failed to read skills from " + skillsDir + ": " + e.getMessage();
        }
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return "Missing file: " + path;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read " + path + ": " + e.getMessage();
        }
    }
}
