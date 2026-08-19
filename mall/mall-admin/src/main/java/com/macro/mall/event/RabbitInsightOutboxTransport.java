package com.macro.mall.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 生产 Outbox 传输端口；P9 洞察事件和既有商城事件保持不同通道。 */
@Component
@Primary
public final class RabbitInsightOutboxTransport implements OutboxTransport {
    static final String INSIGHT_EVENTS_CHANNEL = "insight.events.v1";
    private final RabbitTemplate rabbit;

    public RabbitInsightOutboxTransport(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void send(EventEnvelope event) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("eventId", event.getEventId().toString());
        message.put("eventType", event.getEventType());
        message.put("eventVersion", event.getEventVersion());
        message.put("occurredAt", event.getOccurredAt().toString());
        message.put("tenantId", event.getTenantId());
        message.put("traceId", event.getTraceId());
        message.put("producer", event.getProducer());
        message.put("payload", event.getPayload());
        rabbit.convertAndSend(channel(event), message);
    }

    private static String channel(EventEnvelope event) {
        return "logistics.exception".equals(event.getEventType()) ? INSIGHT_EVENTS_CHANNEL : "mall.events.v1";
    }
}
