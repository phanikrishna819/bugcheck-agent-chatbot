package com.bugcheck.agent.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatSession {
    private final String id;
    private final Instant createdAt;
    private String workspaceRoot;
    private boolean allowEdits;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatSession(String id, String workspaceRoot, boolean allowEdits) {
        this.id = id;
        this.workspaceRoot = workspaceRoot;
        this.allowEdits = allowEdits;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public boolean isAllowEdits() {
        return allowEdits;
    }

    public void setAllowEdits(boolean allowEdits) {
        this.allowEdits = allowEdits;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
}
