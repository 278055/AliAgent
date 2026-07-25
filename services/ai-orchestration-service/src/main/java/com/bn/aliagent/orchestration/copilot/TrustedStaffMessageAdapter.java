package com.bn.aliagent.orchestration.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

public final class TrustedStaffMessageAdapter implements CopilotPorts.StaffMessagePort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedCopilotHttpClient client;

    public TrustedStaffMessageAdapter(String baseUrl, String serviceJwt, int timeoutMs, int attempts) {
        this.baseUrl = baseUrl;
        this.client = new TrustedCopilotHttpClient(serviceJwt, "conversation-service", timeoutMs, attempts);
    }

    @Override
    public void send(String tenantId, UUID conversationId, String agentId, String content, UUID requestId) {
        try {
            client.post(baseUrl + "/internal/api/v1/conversations/" + conversationId + "/staff-messages",
                    JSON.writeValueAsString(Map.of("content", content, "clientMessageId", requestId)),
                    tenantId, agentId, "STAFF", "STAFF", "", null, requestId);
        } catch (CopilotException exception) { throw exception;
        } catch (Exception exception) { throw new CopilotException("cannot encode staff message"); }
    }
}
