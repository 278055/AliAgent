package com.bn.aliagent.insight.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightFactProjectorTest {
    @Test
    void 仅将白名单字段投影为匿名事实() {
        var projector = new InsightFactProjector(new DeterministicFactAnonymizer("test-key-for-p9-anonymization"));
        var event = new InsightEventEnvelope(UUID.randomUUID(), "order.paid", 1, Instant.parse("2026-07-31T00:00:00Z"),
                "test-p9-a", "trace", "mall", Map.of("orderId", "order-1", "amount", new BigDecimal("12.30"),
                        "phone", "13800138000", "extra", "not-projected"));

        var fact = projector.project(event);

        assertEquals(new BigDecimal("12.30"), fact.measures().get("amount"));
        assertTrue(fact.evidenceRefs().get("orderId").startsWith("ref-"));
        assertFalse(fact.dimensions().containsKey("phone"));
        assertFalse(fact.dimensions().containsKey("extra"));
    }
}
