package com.bugcheck.agent.api.dto;

import com.bugcheck.agent.chat.ChatMessage;
import java.util.List;

public record ChatSessionResponse(
        String id,
        String workspaceRoot,
        boolean allowEdits,
        List<ChatMessage> messages
) {
}
