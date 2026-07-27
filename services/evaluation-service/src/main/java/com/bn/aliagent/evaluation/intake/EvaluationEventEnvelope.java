package com.bn.aliagent.evaluation.intake;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvaluationEventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                                      String tenantId, String traceId, String producer, Map<String, Object> payload) {
    public EvaluationEventEnvelope {
        if (eventId == null || eventType == null || eventType.isBlank() || occurredAt == null || tenantId == null || tenantId.isBlank()
                || traceId == null || traceId.isBlank() || producer == null || producer.isBlank() || payload == null) {
            throw new IllegalArgumentException("事件信封缺少必要元数据");
        }
    }
}
