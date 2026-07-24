package com.bn.aliagent.orchestration.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvocationAuditServiceTest {
    @Test
    void recordsToolOutcomeLatencyAndDegradationWithoutSensitiveFields() {
        InvocationAuditService service = new InvocationAuditService();
        service.recordTool(UUID.randomUUID(), "test-p5-c-tenant", "mall", "order", "DEGRADED", 12L, "MALL_UNAVAILABLE",
                Map.of("authorization", "secret", "orderId", 1));

        InvocationAudit record = service.records().get(0);
        assertEquals(12L, record.latencyMs());
        assertEquals("MALL_UNAVAILABLE", record.degradationReason());
        assertFalse(record.attributes().containsKey("authorization"));
    }
}
