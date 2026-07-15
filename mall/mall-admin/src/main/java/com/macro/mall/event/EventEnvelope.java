package com.macro.mall.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 统一事件信封；序列化字段与平台事件契约保持一致。 */
public class EventEnvelope {
    private UUID eventId;
    private String eventType;
    private int eventVersion;
    private Instant occurredAt;
    private String tenantId;
    private String traceId;
    private String producer;
    private Map<String, Object> payload;

    public EventEnvelope() { }

    public EventEnvelope(UUID eventId, String eventType, int eventVersion, Instant occurredAt,
                         String tenantId, String traceId, String producer, Map<String, Object> payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.occurredAt = occurredAt;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.producer = producer;
        this.payload = payload;
    }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public int getEventVersion() { return eventVersion; }
    public void setEventVersion(int eventVersion) { this.eventVersion = eventVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getProducer() { return producer; }
    public void setProducer(String producer) { this.producer = producer; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
