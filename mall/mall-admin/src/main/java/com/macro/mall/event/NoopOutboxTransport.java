package com.macro.mall.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** RabbitMQ 未接入前的安全占位；只记录，不伪造已投递。 */
@Component
public class NoopOutboxTransport implements OutboxTransport {
    private static final Logger log = LoggerFactory.getLogger(NoopOutboxTransport.class);
    @Override public void send(EventEnvelope event) { log.info("Outbox待接入传输端口 eventId={}, type={}", event.getEventId(), event.getEventType()); }
}
