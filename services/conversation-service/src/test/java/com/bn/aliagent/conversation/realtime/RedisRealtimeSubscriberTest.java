package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RedisRealtimeSubscriberTest {
    @Test
    void subscriptionUsesConcreteTenantAndInstanceChannel() {
        String channel = RedisRealtimeSubscriber.channel("test-p4-c-tenant", "instance-b");

        assertEquals("conversation:test-p4-c-tenant:instance:instance-b:events", channel);
        assertFalse(channel.contains("*"));
    }

    @Test
    void tracksEachTrustedTenantOnlyOnce() {
        RedisRealtimeSubscriber subscriber = new RedisRealtimeSubscriber("localhost", 1, "", "instance-b",
                new RealtimeSessionRegistry());

        subscriber.subscribeTenant("test-p4-c-tenant");
        subscriber.subscribeTenant("test-p4-c-tenant");

        assertEquals(1, subscriber.subscribedTenantCount());
        subscriber.unsubscribeTenant("test-p4-c-tenant");
        assertEquals(0, subscriber.subscribedTenantCount());
        subscriber.close();
    }
}
