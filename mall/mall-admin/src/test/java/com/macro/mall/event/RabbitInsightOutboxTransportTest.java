package com.macro.mall.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitInsightOutboxTransportTest {

    @Test
    void sendsEnvelopeToTheFrozenInsightChannel() {
        RabbitTemplate rabbit = org.mockito.Mockito.mock(RabbitTemplate.class);
        RabbitInsightOutboxTransport transport = new RabbitInsightOutboxTransport(rabbit);
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), "logistics.exception", 1, Instant.now(),
                "test-tenant", "test-trace", "mall-admin", Map.of("evidenceRef", "order-1", "reasonCode", "X"));

        transport.send(event);

        verify(rabbit).convertAndSend(eq("insight.events.v1"), org.mockito.ArgumentMatchers.<Object>argThat(message -> {
            Map<String, Object> body = (Map<String, Object>) message;
            return "logistics.exception".equals(body.get("eventType"))
                    && event.getEventId().toString().equals(body.get("eventId"))
                    && "test-tenant".equals(body.get("tenantId"));
        }));
    }
}
