package com.bugcheck.agent.openai;

import com.bugcheck.agent.asset.AgentAssetService;
import com.bugcheck.agent.chat.ChatMessage;
import com.bugcheck.agent.chat.ChatSession;
import com.bugcheck.agent.config.AgentProperties;
import com.bugcheck.agent.config.OpenAiProperties;
import com.bugcheck.agent.tools.ToolResult;
import com.bugcheck.agent.tools.WorkspaceTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiAgentService {
    private final OpenAiProperties openAiProperties;
    private final AgentProperties agentProperties;
    private final AgentAssetService assetService;
    private final WorkspaceTools workspaceTools;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiAgentService(OpenAiProperties openAiProperties,
                              AgentProperties agentProperties,
                              AgentAssetService assetService,
                              WorkspaceTools workspaceTools,
                              ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.agentProperties = agentProperties;
        this.assetService = assetService;
        this.workspaceTools = workspaceTools;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(openAiProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public String respond(ChatSession session, String userMessage) {
        if (openAiProperties.apiKey() == null || openAiProperties.apiKey().isBlank()) {
            return "OPENAI_API_KEY is not configured. Set it and restart the backend.";
        }

        List<Map<String, Object>> messages = buildMessages(session, userMessage);
        for (int round = 0; round < Math.max(1, agentProperties.maxToolRounds()); round++) {
            JsonNode response = chatCompletion(messages);
            JsonNode message = response.path("choices").path(0).path("message");
            Map<String, Object> assistantMessage = objectMapper.convertValue(message, Map.class);
            messages.add(assistantMessage);

            JsonNode toolCalls = message.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String content = message.path("content").asText("");
                return content.isBlank() ? "The agent returned an empty response." : content;
            }

            for (JsonNode toolCall : toolCalls) {
                String id = toolCall.path("id").asText();
                String name = toolCall.path("function").path("name").asText();
                String args = toolCall.path("function").path("arguments").asText("{}");
                ToolResult result = workspaceTools.execute(name, args, session.isAllowEdits());
                session.getMessages().add(ChatMessage.tool(name + ": " + (result.ok() ? "OK" : "ERROR") + "\n" + result.output()));
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", id,
                        "content", result.output()
                ));
            }
        }
        return "The agent reached the maximum tool-call rounds. Ask it to continue if more work is needed.";
    }

    private List<Map<String, Object>> buildMessages(ChatSession session, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", assetService.buildSystemPrompt()));
        messages.add(Map.of("role", "system", "content", "Current registered workspace: " + session.getWorkspaceRoot() + "\nEdits allowed: " + session.isAllowEdits()));
        for (ChatMessage message : session.getMessages()) {
            if ("tool".equals(message.role())) {
                continue;
            }
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }
        return messages;
    }

    private JsonNode chatCompletion(List<Map<String, Object>> messages) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", openAiProperties.model());
        request.put("messages", messages);
        request.put("tools", workspaceTools.definitions());
        request.put("tool_choice", "auto");
        request.put("temperature", 0.1);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
