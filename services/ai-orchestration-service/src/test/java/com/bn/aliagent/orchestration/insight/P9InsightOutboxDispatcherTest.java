package com.bn.aliagent.orchestration.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class P9InsightOutboxDispatcherTest {
    @Test void 拒绝未经契约允许的AI事件类型() {
        assertThrows(IllegalArgumentException.class, () -> new P9InsightEvent(
                UUID.randomUUID(), "ai.sql.executed", "test-tenant", "trace", Instant.now(), "execution-1"));
    }

    @Test void 查询计划信号不包含计划内容或用户输入() {
        P9InsightEvent event = new P9InsightEvent(UUID.randomUUID(), "ai.query-plan.accepted",
                "test-tenant", "trace", Instant.now(), "execution-1");

        assertEquals(java.util.Map.of("evidenceRef", "execution-1", "signal", "query-plan-accepted"),
                ((java.util.Map<?, ?>) event.envelope().get("payload")));
    }
    @Test void leavesEventPendingWhenPublishFails() {
        FakeOutbox outbox = new FakeOutbox(); outbox.events.add(event());
        new P9InsightOutboxDispatcher(outbox, value -> { throw new IllegalStateException("mq down"); }).dispatchPending();
        assertEquals(0, outbox.published.size());
    }
    @Test void marksEventPublishedAfterBrokerAcceptsIt() {
        FakeOutbox outbox = new FakeOutbox(); outbox.events.add(event());
        new P9InsightOutboxDispatcher(outbox, value -> { }).dispatchPending();
        assertEquals(1, outbox.published.size());
    }
    private static P9InsightEvent event() { return new P9InsightEvent(UUID.randomUUID(), "ai.rag.completed", "test-tenant", "trace", Instant.now(), "execution-1"); }
    private static final class FakeOutbox implements P9InsightOutbox {
        private final List<P9InsightEvent> events = new ArrayList<>(); private final List<UUID> published = new ArrayList<>();
        public void append(String tenantId, String traceId, UUID executionId, String eventType) { }
        public List<P9InsightEvent> pending(int limit) { return events; }
        public void markPublished(UUID eventId) { published.add(eventId); }
    }
}
