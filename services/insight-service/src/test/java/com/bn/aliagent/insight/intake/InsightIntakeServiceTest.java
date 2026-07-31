package com.bn.aliagent.insight.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.insight.fact.AnonymizedFact;
import com.bn.aliagent.insight.fact.InsightFactSink;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightIntakeServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void 同一生产者重复事件只投影一次() {
        var inbox = new InsightEventInbox.InMemory();
        var facts = new RecordingFactSink();
        var service = service(inbox, facts, payload -> fact(payload));
        var event = event("test-p9-a", "conversation-service", UUID.randomUUID());

        service.accept(event, "test-p9-a");
        service.accept(event, "test-p9-a");

        assertEquals(1, facts.values.size());
        assertEquals(IntakeStatus.COMPLETED, inbox.require("test-p9-a", "conversation-service", event.eventId()).status());
    }

    @Test
    void 不同生产者相同事件标识不冲突() {
        var facts = new RecordingFactSink();
        var service = service(new InsightEventInbox.InMemory(), facts, payload -> fact(payload));
        UUID eventId = UUID.randomUUID();

        service.accept(event("test-p9-a", "mall", eventId), "test-p9-a");
        service.accept(event("test-p9-a", "conversation-service", eventId), "test-p9-a");

        assertEquals(2, facts.values.size());
    }

    @Test
    void 不可信租户和未知事件策略被拒绝() {
        var service = service(new InsightEventInbox.InMemory(), new RecordingFactSink(), payload -> fact(payload));
        var event = event("test-p9-a", "conversation-service", UUID.randomUUID());

        assertThrows(SecurityException.class, () -> service.accept(event, "test-other"));
        assertThrows(IllegalArgumentException.class, () -> service.accept(new InsightEventEnvelope(event.eventId(), "unknown", 1,
                NOW, event.tenantId(), event.traceId(), event.producer(), event.payload()), "test-p9-a"));
        assertThrows(IllegalArgumentException.class, () -> service.accept(new InsightEventEnvelope(event.eventId(), event.eventType(), 2,
                NOW, event.tenantId(), event.traceId(), event.producer(), event.payload()), "test-p9-a"));
    }

    @Test
    void 失败后可重试且不会产生第二份事实() {
        var inbox = new InsightEventInbox.InMemory();
        var facts = new RecordingFactSink();
        var first = new boolean[] {true};
        var service = service(inbox, facts, payload -> {
            if (first[0]) { first[0] = false; throw new IllegalStateException("处理失败"); }
            return fact(payload);
        });
        var event = event("test-p9-a", "conversation-service", UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> service.accept(event, "test-p9-a"));
        service.accept(event, "test-p9-a");
        service.accept(event, "test-p9-a");

        assertEquals(1, facts.values.size());
        assertEquals(IntakeStatus.COMPLETED, inbox.require("test-p9-a", event.producer(), event.eventId()).status());
    }

    private InsightIntakeService service(InsightEventInbox inbox, InsightFactSink facts, InsightFactProjector projector) {
        return new InsightIntakeService(new InsightEventPolicy(Map.of("conversation.completed", Set.of(1))), inbox,
                projector, facts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AnonymizedFact fact(InsightEventEnvelope event) {
        return new AnonymizedFact(UUID.randomUUID(), event.tenantId(), "CONVERSATION", event.occurredAt(), Map.of(), Map.of(),
                Map.of(), event.eventId(), 1, null, "test");
    }

    private InsightEventEnvelope event(String tenantId, String producer, UUID eventId) {
        return new InsightEventEnvelope(eventId, "conversation.completed", 1, NOW, tenantId, "trace-1", producer, Map.of("text", "safe"));
    }

    private static final class RecordingFactSink implements InsightFactSink {
        private final ArrayList<AnonymizedFact> values = new ArrayList<>();
        public void append(AnonymizedFact fact) { values.add(fact); }
    }
}
