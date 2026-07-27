package com.bn.aliagent.evaluation.intake;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EventInbox {
    Optional<IntakeRecord> reserve(EvaluationEventEnvelope event, String contentDigest, Instant now);
    void complete(UUID eventId, Instant now);
    void fail(UUID eventId, Instant now);
    IntakeRecord require(UUID eventId);

    final class InMemory implements EventInbox {
        private final Map<UUID, IntakeRecord> records = new LinkedHashMap<>();
        @Override public synchronized Optional<IntakeRecord> reserve(EvaluationEventEnvelope event, String contentDigest, Instant now) {
            IntakeRecord current = records.get(event.eventId());
            if (current != null && current.status() != IntakeStatus.FAILED) return Optional.empty();
            IntakeRecord record = new IntakeRecord(event.eventId(), event.tenantId(), event.eventType(), event.eventVersion(), contentDigest, IntakeStatus.RESERVED, now);
            records.put(event.eventId(), record);
            return Optional.of(record);
        }
        @Override public synchronized void complete(UUID eventId, Instant now) { update(eventId, IntakeStatus.COMPLETED, now); }
        @Override public synchronized void fail(UUID eventId, Instant now) { update(eventId, IntakeStatus.FAILED, now); }
        @Override public synchronized IntakeRecord require(UUID eventId) {
            IntakeRecord value = records.get(eventId);
            if (value == null) throw new IllegalArgumentException("事件未预留");
            return value;
        }
        private void update(UUID eventId, IntakeStatus status, Instant now) {
            IntakeRecord current = require(eventId);
            records.put(eventId, new IntakeRecord(current.eventId(), current.tenantId(), current.eventType(), current.eventVersion(), current.contentDigest(), status, now));
        }
    }
}
