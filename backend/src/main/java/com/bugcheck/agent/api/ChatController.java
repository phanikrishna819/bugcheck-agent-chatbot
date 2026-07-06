package com.bugcheck.agent.api;

import com.bugcheck.agent.api.dto.ChatMessageRequest;
import com.bugcheck.agent.api.dto.ChatSessionRequest;
import com.bugcheck.agent.api.dto.ChatSessionResponse;
import com.bugcheck.agent.chat.ChatMessage;
import com.bugcheck.agent.chat.ChatSession;
import com.bugcheck.agent.chat.ChatSessionService;
import com.bugcheck.agent.openai.OpenAiAgentService;
import com.bugcheck.agent.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {
    private final ChatSessionService chatSessionService;
    private final OpenAiAgentService openAiAgentService;
    private final WorkspaceService workspaceService;

    public ChatController(ChatSessionService chatSessionService, OpenAiAgentService openAiAgentService, WorkspaceService workspaceService) {
        this.chatSessionService = chatSessionService;
        this.openAiAgentService = openAiAgentService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<ChatSessionResponse> list() {
        return chatSessionService.list();
    }

    @PostMapping
    public ChatSessionResponse create(@RequestBody ChatSessionRequest request) {
        String workspace = request.workspaceRoot();
        if (workspace != null && !workspace.isBlank()) {
            workspace = workspaceService.register(workspace);
        }
        return chatSessionService.toResponse(chatSessionService.create(workspace, request.allowEdits()));
    }

    @GetMapping("/{id}")
    public ChatSessionResponse get(@PathVariable String id) {
        return chatSessionService.toResponse(chatSessionService.get(id));
    }

    @PostMapping("/{id}/messages")
    public ChatSessionResponse send(@PathVariable String id, @Valid @RequestBody ChatMessageRequest request) {
        ChatSession session = chatSessionService.get(id);
        if (request.workspaceRoot() != null && !request.workspaceRoot().isBlank()) {
            session.setWorkspaceRoot(workspaceService.register(request.workspaceRoot()));
        }
        session.setAllowEdits(request.allowEdits());
        session.getMessages().add(ChatMessage.user(request.content()));
        String response = openAiAgentService.respond(session, request.content());
        session.getMessages().add(ChatMessage.assistant(response));
        return chatSessionService.toResponse(session);
    }
}
