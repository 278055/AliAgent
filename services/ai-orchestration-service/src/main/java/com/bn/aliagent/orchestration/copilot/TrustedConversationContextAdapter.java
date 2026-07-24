package com.bn.aliagent.orchestration.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TrustedConversationContextAdapter implements CopilotPorts.ConversationContextPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedCopilotHttpClient client;

    public TrustedConversationContextAdapter(String baseUrl, String serviceJwt, int timeoutMs, int attempts) {
        this.baseUrl = baseUrl;
        this.client = new TrustedCopilotHttpClient(serviceJwt, timeoutMs, attempts);
    }

    @Override
    public CopilotPorts.ConversationContext load(String tenantId, UUID conversationId) {
        try {
            JsonNode body = JSON.readTree(client.get(baseUrl + "/internal/api/v1/conversations/" + conversationId + "/copilot-context",
                    tenantId, "copilot-service", "SERVICE", conversationId));
            if (!tenantId.equals(body.path("tenantId").asText()) || !conversationId.toString().equals(body.path("conversationId").asText())) {
                throw new CopilotException("trusted conversation context does not match request");
            }
            List<String> messages = new ArrayList<>();
            for (JsonNode message : body.path("messages")) messages.add(message.asText());
            Long orderId = body.hasNonNull("linkedOrderId") ? body.path("linkedOrderId").asLong() : null;
            return new CopilotPorts.ConversationContext(tenantId, conversationId, body.path("collaborationState").asText(),
                    body.path("assignedAgentId").asText(), messages, orderId,
                    body.hasNonNull("linkedAfterSaleId") ? body.path("linkedAfterSaleId").asText() : null);
        } catch (CopilotException exception) { throw exception;
        } catch (Exception exception) { throw new CopilotException("invalid trusted conversation context"); }
    }
}
