package com.macro.mall.portal.aftersale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.macro.mall.portal.aftersale.messaging.P9RefundEventMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class P9RefundEventMapperTest {
    @Test
    public void refundEventContainsOnlyContractFields() {
        Map<String, Object> event = P9RefundEventMapper.map("test-tenant", "trace-1", 7L, new BigDecimal("12.30"), "2026-08-01T00:00:00Z");
        assertEquals("refund.succeeded", event.get("eventType"));
        assertFalse(event.toString().contains("member"));
        assertFalse(event.toString().contains("address"));
    }
}
