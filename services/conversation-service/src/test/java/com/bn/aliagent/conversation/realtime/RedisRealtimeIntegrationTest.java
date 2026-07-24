package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisRealtimeIntegrationTest {
    @Test
    void publisherRejectsChannelWithoutSubscriber() {
        RedisRealtimePublisher publisher = new RedisRealtimePublisher("localhost", 6600, "123456");
        assertThrows(IllegalStateException.class, () -> publisher.publish(
                "conversation:test-p4-c-tenant:instance:missing:events",
                new RealtimeEnvelope("human.message", "test-p4-c-tenant", UUID.randomUUID(), UUID.randomUUID(),
                        Instant.now(), UUID.randomUUID(), 1, "STAFF", "测试人工消息", null)));
    }

    @Test
    void twoInstancesDeliverThroughAuthenticatedTenantChannel() throws Exception {
        String tenantId = "test-p4-c-tenant";
        UUID conversationId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        RealtimeSessionRegistry targetSessions = new RealtimeSessionRegistry();
        Session session = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        targetSessions.add(connectionId, tenantId, session);

        RedisRealtimeSubscriber target = new RedisRealtimeSubscriber("localhost", 6600, "123456", "test-p4-c-b", targetSessions);
        target.subscribeTenant(tenantId);
        RedisRealtimeRouteStore routes = new RedisRealtimeRouteStore("localhost", 6600, "123456", 30);
        RealtimeCollaborationService source = new RealtimeCollaborationService("test-p4-c-a", routes,
                new RedisRealtimePublisher("localhost", 6600, "123456"));
        source.register(new RealtimeConnection(tenantId, conversationId, connectionId, "test-p4-c-b"));
        try {
            assertEquals(1, routes.find(tenantId, conversationId).size());
            Thread.sleep(100);
            DeliveryResult result = source.deliver(new RealtimeEnvelope("human.message", tenantId, conversationId,
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), 8, "STAFF", "测试人工消息", null));
            assertTrue(result.delivered());
            verify(remote, timeout(3000)).sendText(org.mockito.ArgumentMatchers.contains("测试人工消息"));
        } finally {
            source.unregister(tenantId, connectionId);
            target.close();
        }
    }
}
