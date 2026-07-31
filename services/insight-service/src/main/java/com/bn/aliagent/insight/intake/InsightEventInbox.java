package com.bn.aliagent.insight.intake;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InsightEventInbox {
    Optional<InsightIntakeRecord> reserve(InsightEventEnvelope event, String digest, Instant now);
    void complete(String tenantId, String producer, UUID eventId, Instant now);
    void fail(String tenantId, String producer, UUID eventId, Instant now);
    InsightIntakeRecord require(String tenantId, String producer, UUID eventId);

    final class InMemory implements InsightEventInbox {
        private final Map<Key, InsightIntakeRecord> records = new HashMap<>();
        public synchronized Optional<InsightIntakeRecord> reserve(InsightEventEnvelope event, String digest, Instant now) {
            Key key = new Key(event.tenantId(), event.producer(), event.eventId());
            InsightIntakeRecord current = records.get(key);
            if (current != null && current.status() != IntakeStatus.FAILED) return Optional.empty();
            InsightIntakeRecord next = new InsightIntakeRecord(key.tenantId, key.producer, key.eventId, digest,
                    IntakeStatus.PROCESSING, current == null ? now : current.createdAt(), now);
            records.put(key, next);
            return Optional.of(next);
        }
        public synchronized void complete(String tenantId, String producer, UUID eventId, Instant now) { update(tenantId, producer, eventId, IntakeStatus.COMPLETED, now); }
        public synchronized void fail(String tenantId, String producer, UUID eventId, Instant now) { update(tenantId, producer, eventId, IntakeStatus.FAILED, now); }
        public synchronized InsightIntakeRecord require(String tenantId, String producer, UUID eventId) {
            InsightIntakeRecord record = records.get(new Key(tenantId, producer, eventId));
            if (record == null) throw new IllegalArgumentException("未找到事件摄入记录");
            return record;
        }
        private void update(String tenantId, String producer, UUID eventId, IntakeStatus status, Instant now) {
            Key key = new Key(tenantId, producer, eventId);
            records.put(key, require(tenantId, producer, eventId).withStatus(status, now));
        }
        private record Key(String tenantId, String producer, UUID eventId) { }
    }
}
