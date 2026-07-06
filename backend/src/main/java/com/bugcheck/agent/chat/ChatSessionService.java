package com.bugcheck.agent.chat;

import com.bugcheck.agent.api.dto.ChatSessionResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatSessionService {
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession create(String workspaceRoot, boolean allowEdits) {
        ChatSession session = new ChatSession(UUID.randomUUID().toString(), workspaceRoot, allowEdits);
        session.getMessages().add(ChatMessage.assistant("Bugcheck agent ready. Select a workspace and ask me to inspect a diff, run checks, or apply fixes."));
        sessions.put(session.getId(), session);
        return session;
    }

    public ChatSession get(String id) {
        ChatSession session = sessions.get(id);
        if (session == null) {
            throw new IllegalArgumentException("Chat session not found: " + id);
        }
        return session;
    }

    public List<ChatSessionResponse> list() {
        return sessions.values().stream()
                .sorted(Comparator.comparing(ChatSession::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    public ChatSessionResponse toResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getWorkspaceRoot(),
                session.isAllowEdits(),
                new ArrayList<>(session.getMessages())
        );
    }
}
