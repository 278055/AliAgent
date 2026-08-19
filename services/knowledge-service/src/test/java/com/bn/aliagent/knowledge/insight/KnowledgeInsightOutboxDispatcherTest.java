package com.bn.aliagent.knowledge.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeInsightOutboxDispatcherTest {
    @Test
    void keepsKnowledgePublicationPendingWhenBrokerFails() {
        FakeOutbox outbox = new FakeOutbox();
        outbox.pending.add(event());
        new KnowledgeInsightOutboxDispatcher(outbox, value -> { throw new IllegalStateException("broker unavailable"); }).dispatchPending();
        assertEquals(0, outbox.published.size());
    }

    @Test
    void marksKnowledgePublicationPublishedOnlyAfterBrokerAcceptsIt() {
        FakeOutbox outbox = new FakeOutbox();
        outbox.pending.add(event());
        new KnowledgeInsightOutboxDispatcher(outbox, value -> { }).dispatchPending();
        assertEquals(1, outbox.published.size());
    }

    private static KnowledgeInsightEvent event() { return new KnowledgeInsightEvent(UUID.randomUUID(), "knowledge.published", "test-tenant", "trace-1", Instant.now(), UUID.randomUUID().toString(), "digest-1"); }
    private static final class FakeOutbox implements KnowledgeInsightOutbox {
        private final List<KnowledgeInsightEvent> pending = new ArrayList<>(); private final List<UUID> published = new ArrayList<>();
        public void append(String tenant, String trace, UUID version, String digest) { }
        public List<KnowledgeInsightEvent> pending(int limit) { return pending; }
        public void markPublished(UUID id) { published.add(id); }
    }
}
