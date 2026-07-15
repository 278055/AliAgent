package com.macro.mall.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;

@Component
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxEventMapper mapper;
    private final OutboxTransport transport;
    private final ObjectMapper objectMapper;

    public OutboxDispatcher(OutboxEventMapper mapper, OutboxTransport transport, ObjectMapper objectMapper) {
        this.mapper = mapper; this.transport = transport; this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${mall.outbox.poll-delay-ms:1000}")
    public void dispatch() {
        for (OutboxEvent row : mapper.findDue(Instant.now(), 100)) {
            try {
                EventEnvelope event = new EventEnvelope();
                event.setEventId(row.getEventId()); event.setEventType(row.getEventType());
                event.setEventVersion(row.getEventVersion()); event.setOccurredAt(row.getOccurredAt());
                event.setTenantId(row.getTenantId()); event.setTraceId(row.getTraceId()); event.setProducer(row.getProducer());
                event.setPayload(objectMapper.readValue(row.getPayload(), Map.class));
                transport.send(event);
                mapper.markPublished(row.getEventId(), Instant.now());
            } catch (Exception e) {
                int attempts = row.getAttempts() + 1;
                long delay = Math.min(3600, 1L << Math.min(attempts, 12));
                mapper.markFailed(row.getEventId(), attempts, Instant.now().plusSeconds(delay), e.getMessage(), attempts >= 10 ? "DEAD" : "RETRY");
                log.warn("Outbox投递失败 eventId={}, attempts={}", row.getEventId(), attempts, e);
            }
        }
    }
}
