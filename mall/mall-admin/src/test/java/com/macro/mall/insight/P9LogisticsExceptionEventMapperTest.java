package com.macro.mall.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class P9LogisticsExceptionEventMapperTest {

    @Test
    void mapsOnlyAnonymizedLogisticsExceptionFacts() {
        Map<String, Object> event = P9LogisticsExceptionEventMapper.map(
                "00000000-0000-0000-0000-000000000009", "test-tenant", "test-trace", 42L,
                "MISSING_TRACKING_NUMBER", Instant.parse("2026-08-19T00:00:00Z"));

        assertEquals("logistics.exception", event.get("eventType"));
        assertEquals("test-tenant", event.get("tenantId"));
        assertEquals("mall-admin", event.get("producer"));
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertEquals("order-42", payload.get("evidenceRef"));
        assertEquals("MISSING_TRACKING_NUMBER", payload.get("reasonCode"));
        assertFalse(payload.containsKey("orderId"));
        assertFalse(payload.containsKey("trackingNo"));
    }
}
