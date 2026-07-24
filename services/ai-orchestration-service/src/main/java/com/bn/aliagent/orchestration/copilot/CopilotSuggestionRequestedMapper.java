package com.bn.aliagent.orchestration.copilot;

import java.util.Map;
import java.util.UUID;

public final class CopilotSuggestionRequestedMapper {
    @SuppressWarnings("unchecked")
    public CopilotModels.SuggestionRequested map(Map<String, Object> event) {
        if (event == null || number(event.get("eventVersion")) != 1) throw new IllegalArgumentException("unsupported copilot event version");
        String tenantId = required(event, "tenantId");
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        if (payload == null) throw new IllegalArgumentException("copilot event payload is required");
        return new CopilotModels.SuggestionRequested(uuid(event, "eventId"), new CopilotModels.GenerateCommand(
                uuid(payload, "requestId"), tenantId, uuid(payload, "conversationId"), uuid(payload, "triggerMessageId"),
                required(payload, "assignedAgentId"), required(payload, "assignedAgentId"), number(payload.get("refreshNo")),
                required(payload, "modelVersion"), required(payload, "promptVersion"), required(payload, "workflowVersion")));
    }
    private static String required(Map<String, Object> value, String key) { Object item = value.get(key); if (item == null || item.toString().isBlank()) throw new IllegalArgumentException(key + " is required"); return item.toString(); }
    private static UUID uuid(Map<String, Object> value, String key) { return UUID.fromString(required(value, key)); }
    private static int number(Object value) { if (!(value instanceof Number number)) throw new IllegalArgumentException("refreshNo is required"); return number.intValue(); }
}
