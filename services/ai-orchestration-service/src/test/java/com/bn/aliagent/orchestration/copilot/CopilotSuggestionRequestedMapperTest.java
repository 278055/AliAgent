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

    @Test
    void mapsVersionTwoEventWithGatewayIssuedAuthorizationSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("conversationId", UUID.randomUUID().toString()); payload.put("triggerMessageId", UUID.randomUUID().toString());
        payload.put("assignedAgentId", "staff-1"); payload.put("requestId", UUID.randomUUID().toString()); payload.put("refreshNo", 0);
        payload.put("modelVersion", "model-v1"); payload.put("promptVersion", "prompt-v1"); payload.put("workflowVersion", "workflow-v1");
        payload.put("authorizationSnapshotId", snapshotId.toString()); payload.put("subjectId", "member-1");
        payload.put("subjectType", "MEMBER"); payload.put("roles", "MEMBER"); payload.put("permissions", "KNOWLEDGE_READ");
        Map<String, Object> event = Map.of("eventId", UUID.randomUUID().toString(), "eventVersion", 2,
                "tenantId", "tenant-a", "payload", payload);

        var result = new CopilotSuggestionRequestedMapper().map(event);

        assertEquals(snapshotId, result.command().authorizationSnapshotId());
    }
}
