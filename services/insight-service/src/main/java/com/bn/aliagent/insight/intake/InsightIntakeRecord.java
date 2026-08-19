package com.bn.aliagent.insight.intake;

import java.time.Instant;
import java.util.UUID;

public record InsightIntakeRecord(String tenantId, String producer, UUID eventId, String digest,
        IntakeStatus status, Instant createdAt, Instant updatedAt) {
    InsightIntakeRecord withStatus(IntakeStatus next, Instant now) {
        return new InsightIntakeRecord(tenantId, producer, eventId, digest, next, createdAt, now);
    }
}
