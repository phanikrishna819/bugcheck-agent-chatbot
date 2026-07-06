package com.bugcheck.agent.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank String content,
        String workspaceRoot,
        boolean allowEdits
) {
}
