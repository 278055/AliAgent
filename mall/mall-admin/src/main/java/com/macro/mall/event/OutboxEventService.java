package com.macro.mall.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventService implements EventPublisher {
    static final String PENDING = "PENDING";
    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(EventEnvelope event) {
        if (event.getEventId() == null) throw new IllegalArgumentException("eventId不能为空");
        if (event.getEventVersion() != 1) throw new IllegalArgumentException("eventVersion必须为1");
        try {
            OutboxEvent row = new OutboxEvent();
            row.setEventId(event.getEventId()); row.setEventType(event.getEventType());
            row.setEventVersion(event.getEventVersion()); row.setOccurredAt(event.getOccurredAt());
            row.setTenantId(event.getTenantId()); row.setTraceId(event.getTraceId());
            row.setProducer(event.getProducer()); row.setPayload(objectMapper.writeValueAsString(event.getPayload()));
            row.setStatus(PENDING); row.setNextAttemptAt(Instant.now());
            mapper.insert(row);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("事件负载序列化失败", e);
        }
    }

    /** 允许业务服务在已开启的本地事务中写入，数据库主键保证 eventId 幂等。 */

    public EventEnvelope create(String type, String tenantId, String traceId, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), tenantId, traceId, "mall", payload);
    }
}
