package com.bugcheck.agent.workspace;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class WorkspaceService {
    private final Set<String> registeredRoots = new ConcurrentSkipListSet<>();

    public String register(String path) {
        Path root = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Workspace must be an existing directory: " + root);
        }
        registeredRoots.add(root.toString());
        return root.toString();
    }

    public List<String> list() {
        return registeredRoots.stream().toList();
    }

    public Path requireRegisteredRoot(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalArgumentException("A workspace root is required.");
        }
        Path root = Path.of(workspaceRoot).toAbsolutePath().normalize();
        if (!registeredRoots.contains(root.toString())) {
            throw new IllegalArgumentException("Workspace is not registered: " + root);
        }
        return root;
    }

    public Path resolveInside(String workspaceRoot, String relativePath) {
        Path root = requireRegisteredRoot(workspaceRoot);
        Path resolved = root.resolve(relativePath == null ? "" : relativePath).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        return resolved;
    }

    public String relativize(Path root, Path path) {
        try {
            return root.toRealPath().relativize(path.toRealPath()).toString().replace('\\', '/');
        } catch (IOException e) {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        }
    }
}
