package com.bn.aliagent.knowledge.insight;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record KnowledgeInsightEvent(UUID eventId, String eventType, String tenantId, String traceId,
        Instant occurredAt, String evidenceRef, String coverageVersion) {
    public Map<String, Object> envelope() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceRef", evidenceRef);
        payload.put("coverageVersion", coverageVersion);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId.toString()); result.put("eventType", eventType); result.put("eventVersion", 1);
        result.put("occurredAt", occurredAt.toString()); result.put("tenantId", tenantId); result.put("traceId", traceId);
        result.put("producer", "knowledge-service"); result.put("payload", payload);
        return Map.copyOf(result);
    }
}
