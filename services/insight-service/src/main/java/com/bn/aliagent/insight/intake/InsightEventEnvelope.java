package com.bn.aliagent.insight.intake;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InsightEventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
        String tenantId, String traceId, String producer, Map<String, Object> payload) {
    public InsightEventEnvelope {
        payload = Map.copyOf(payload);
    }
}
