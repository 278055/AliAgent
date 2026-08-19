package com.macro.mall.portal.aftersale.messaging;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 支付事件只传递匿名证据、订单归属周期与金额。 */
public final class P9OrderPaidEventMapper {
    private P9OrderPaidEventMapper() { }
    public static Map<String, Object> map(String eventId, String tenantId, String traceId, Long orderId, BigDecimal amount, String orderOccurredAt, String occurredAt) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("evidenceRef", "order-" + orderId); payload.put("orderOccurredAt", orderOccurredAt); payload.put("amount", amount);
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("eventId", eventId); event.put("eventType", "order.paid"); event.put("eventVersion", 1);
        event.put("occurredAt", occurredAt); event.put("tenantId", tenantId); event.put("traceId", traceId);
        event.put("producer", "mall-portal"); event.put("payload", payload); return event;
    }
}
