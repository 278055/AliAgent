package com.bn.aliagent.orchestration.quota;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TenantQuotaGateTest {
    @Test
    void rejectsAfterTenantMinuteQuotaIsExhausted() {
        TenantQuotaGate gate = new TenantQuotaGate(2, 1);

        assertEquals(AdmissionResult.ADMITTED, gate.acquire("test-p5-c-tenant", ScenarioPriority.NORMAL));
        gate.release("test-p5-c-tenant");
        assertEquals(AdmissionResult.ADMITTED, gate.acquire("test-p5-c-tenant", ScenarioPriority.NORMAL));
        gate.release("test-p5-c-tenant");

        assertEquals(AdmissionResult.QUOTA_EXHAUSTED, gate.acquire("test-p5-c-tenant", ScenarioPriority.NORMAL));
    }

    @Test
    void reservesConcurrentCapacityForHigherPriorityScenario() {
        TenantQuotaGate gate = new TenantQuotaGate(10, 2);

        assertEquals(AdmissionResult.ADMITTED, gate.acquire("test-p5-c-tenant", ScenarioPriority.NORMAL));
        assertEquals(AdmissionResult.CONCURRENCY_LIMITED, gate.acquire("test-p5-c-tenant", ScenarioPriority.NORMAL));
        assertEquals(AdmissionResult.ADMITTED, gate.acquire("test-p5-c-tenant", ScenarioPriority.HIGH));
    }
}
