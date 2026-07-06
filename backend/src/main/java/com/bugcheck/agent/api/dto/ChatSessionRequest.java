package com.bugcheck.agent.api.dto;

public record ChatSessionRequest(String workspaceRoot, boolean allowEdits) {
}
