package com.macro.mall.portal.aftersale.messaging;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 售后退款成功仅发送去标识的金额与订单周期事实。 */
public final class P9RefundEventMapper {
    private P9RefundEventMapper() { }
    public static Map<String, Object> map(String tenantId, String traceId, Long caseId, BigDecimal amount, String orderOccurredAt) {
        return map(UUID.randomUUID().toString(), tenantId, traceId, caseId, amount, orderOccurredAt);
    }
    public static Map<String, Object> map(String eventId, String tenantId, String traceId, Long caseId, BigDecimal amount, String orderOccurredAt) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("evidenceRef", "after-sale-" + caseId); payload.put("orderOccurredAt", orderOccurredAt);
        payload.put("amount", amount); payload.put("reasonCode", "REFUND_SUCCEEDED");
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("eventId", eventId); event.put("eventType", "refund.succeeded"); event.put("eventVersion", 1);
        event.put("occurredAt", orderOccurredAt); event.put("tenantId", tenantId); event.put("traceId", traceId);
        event.put("producer", "mall-portal"); event.put("payload", payload); return event;
    }
}
