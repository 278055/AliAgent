package com.macro.mall.insight;

import com.macro.mall.event.EventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 将物流异常转换为 P9 契约允许的最小事实，不携带订单或物流单原文。 */
public final class P9LogisticsExceptionEventMapper {
    private P9LogisticsExceptionEventMapper() {
    }

    public static Map<String, Object> map(String eventId, String tenantId, String traceId, Long orderId,
                                           String reasonCode, Instant occurredAt) {
        EventEnvelope event = event(tenantId, traceId, orderId, reasonCode, occurredAt);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", eventId);
        result.put("eventType", event.getEventType());
        result.put("eventVersion", event.getEventVersion());
        result.put("occurredAt", event.getOccurredAt().toString());
        result.put("tenantId", event.getTenantId());
        result.put("traceId", event.getTraceId());
        result.put("producer", event.getProducer());
        result.put("payload", event.getPayload());
        return result;
    }

    public static EventEnvelope event(String tenantId, String traceId, Long orderId, String reasonCode, Instant occurredAt) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("物流异常必须具有可信租户归属");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceRef", "order-" + orderId);
        payload.put("reasonCode", reasonCode);
        payload.put("occurredAt", occurredAt.toString());
        UUID eventId = UUID.nameUUIDFromBytes(("logistics.exception:" + orderId + ":" + reasonCode)
                .getBytes(StandardCharsets.UTF_8));
        return new EventEnvelope(eventId, "logistics.exception", 1, occurredAt, tenantId, traceId,
                "mall-admin", payload);
    }
}
