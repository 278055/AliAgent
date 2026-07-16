package com.bn.aliagent.knowledge.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionOutboxDispatcherTest {
    @Test
    void keepsEventPendingWhenBrokerPublishFails() {
        FakeGateway gateway = new FakeGateway();
        gateway.pending.add(message());
        IngestionOutboxDispatcher dispatcher = new IngestionOutboxDispatcher(gateway, value -> {
            throw new IllegalStateException("broker unavailable");
        });

        dispatcher.dispatchPending();

        assertEquals(0, gateway.published.size());
    }

    @Test
    void marksEventPublishedAfterBrokerAcceptsIt() {
        FakeGateway gateway = new FakeGateway();
        gateway.pending.add(message());
        List<IngestionTaskMessage> sent = new ArrayList<>();
        IngestionOutboxDispatcher dispatcher = new IngestionOutboxDispatcher(gateway, sent::add);

        dispatcher.dispatchPending();

        assertEquals(1, sent.size());
        assertEquals(List.of("event-1"), gateway.published);
    }

    private IngestionTaskMessage message() {
        return new IngestionTaskMessage("event-1", IngestionTaskMessage.EVENT_TYPE, 1, Instant.now().toString(),
                "tenant-a", "trace-1", IngestionTaskMessage.PRODUCER,
                new IngestionTaskMessage.IngestionPayload("task-1"));
    }

    private static final class FakeGateway implements IngestionOutboxGateway {
        private final List<IngestionTaskMessage> pending = new ArrayList<>();
        private final List<String> published = new ArrayList<>();

        @Override public void enqueue(IngestionTaskMessage message) { pending.add(message); }
        @Override public List<IngestionTaskMessage> pending(int limit) { return List.copyOf(pending); }
        @Override public void markPublished(String eventId) { published.add(eventId); }
    }
}
