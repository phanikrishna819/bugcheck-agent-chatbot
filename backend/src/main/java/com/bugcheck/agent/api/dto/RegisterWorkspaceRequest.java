package com.bugcheck.agent.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterWorkspaceRequest(@NotBlank String path) {
}
