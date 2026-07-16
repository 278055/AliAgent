package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeCollaborationServiceTest {
    @Test
    void routesToAnotherInstanceThroughTenantScopedChannel() {
        InMemoryRouteStore routes = new InMemoryRouteStore();
        RecordingPublisher publisher = new RecordingPublisher();
        RealtimeCollaborationService service = new RealtimeCollaborationService("instance-a", routes, publisher);
        UUID conversationId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        service.register(new RealtimeConnection("tenant-a", conversationId, connectionId, "instance-b"));

        DeliveryResult result = service.deliver(new RealtimeEnvelope("human.message", "tenant-a", conversationId,
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 4, "STAFF", "hello", null));

        assertTrue(result.delivered());
        assertEquals("conversation:tenant-a:instance:instance-b:events", publisher.channel);
        assertEquals(connectionId, publisher.envelope.connectionId());
    }

    @Test
    void rejectsCrossTenantDeliveryAndDoesNotPublish() {
        InMemoryRouteStore routes = new InMemoryRouteStore();
        RecordingPublisher publisher = new RecordingPublisher();
        RealtimeCollaborationService service = new RealtimeCollaborationService("instance-a", routes, publisher);
        UUID conversationId = UUID.randomUUID();
        service.register(new RealtimeConnection("tenant-a", conversationId, UUID.randomUUID(), "instance-b"));

        DeliveryResult result = service.deliver(new RealtimeEnvelope("human.message", "tenant-b", conversationId,
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 4, "STAFF", "hello", null));

        assertFalse(result.delivered());
        assertEquals("TENANT-403-001", result.code());
        assertEquals(0, publisher.published);
    }

    @Test
    void reportsRedisDegradationWithoutClaimingDelivery() {
        RealtimeCollaborationService service = new RealtimeCollaborationService("instance-a", new FailingRouteStore(), new RecordingPublisher());
        DeliveryResult result = service.deliver(new RealtimeEnvelope("human.message", "tenant-a", UUID.randomUUID(),
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 4, "STAFF", "hello", null));

        assertFalse(result.delivered());
        assertEquals("SYSTEM-503-REDIS", result.code());
    }

    private static final class RecordingPublisher implements RealtimePublisher {
        private String channel;
        private RealtimeEnvelope envelope;
        private int published;
        @Override public void publish(String channel, RealtimeEnvelope envelope) { this.channel = channel; this.envelope = envelope; published++; }
    }

    private static final class FailingRouteStore implements RealtimeRouteStore {
        @Override public void bind(RealtimeConnection connection) { throw new IllegalStateException("Redis unavailable"); }
        @Override public void unbind(String tenantId, UUID connectionId) { throw new IllegalStateException("Redis unavailable"); }
        @Override public List<RealtimeConnection> find(String tenantId, UUID conversationId) { throw new IllegalStateException("Redis unavailable"); }
    }
}
