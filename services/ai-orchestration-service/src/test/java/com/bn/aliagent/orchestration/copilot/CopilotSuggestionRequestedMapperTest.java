package com.bn.aliagent.orchestration.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CopilotSuggestionRequestedMapperTest {
    @Test
    void mapsCompleteVersionOneEventWithoutGuessingIdentity() {
        UUID eventId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Map<String, Object> event = Map.of("eventId", eventId.toString(), "eventVersion", 1, "tenantId", "tenant-a", "payload", Map.of(
                "conversationId", UUID.randomUUID().toString(), "triggerMessageId", UUID.randomUUID().toString(), "assignedAgentId", "staff-1",
                "requestId", requestId.toString(), "refreshNo", 0, "modelVersion", "model-v1", "promptVersion", "prompt-v1", "workflowVersion", "workflow-v1"));

        var result = new CopilotSuggestionRequestedMapper().map(event);

        assertEquals(eventId, result.eventId());
        assertEquals(requestId, result.command().requestId());
    }

    @Test
    void rejectsMissingAssignedAgent() {
        Map<String, Object> event = Map.of("eventId", UUID.randomUUID().toString(), "eventVersion", 1, "tenantId", "tenant-a", "payload", Map.of(
                "conversationId", UUID.randomUUID().toString(), "triggerMessageId", UUID.randomUUID().toString(), "requestId", UUID.randomUUID().toString(), "refreshNo", 0));
        assertThrows(IllegalArgumentException.class, () -> new CopilotSuggestionRequestedMapper().map(event));
    }
}
