package com.bn.aliagent.evaluation.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GateEvaluationServiceTest {
    private final GateEvaluationService service = new GateEvaluationService();

    @Test
    void 相对退化超过阈值时拒绝发布() {
        GateResultPort.GateEvaluationResults results = results(false, true, Map.of("intent", new GateResultPort.MetricTotals(0.80, 0.90)));

        GateEvaluationService.GateEvaluation decision = service.evaluate("tenant-a", policy(), results);

        assertEquals(GateEvaluationService.GateStatus.FAIL, decision.status());
        assertEquals("RELATIVE_REGRESSION", decision.reasons().get(0));
    }

    @Test
    void 安全红线优先且不能被总体提升或Judge覆盖() {
        GateResultPort.GateEvaluationResults results = results(true, true, Map.of("intent", new GateResultPort.MetricTotals(0.99, 0.90)));

        GateEvaluationService.GateEvaluation decision = service.evaluate("tenant-a", policy(), results);

        assertEquals(GateEvaluationService.GateStatus.FAIL, decision.status());
        assertEquals(List.of("SAFETY_REDLINE:PRIVACY_LEAK"), decision.reasons());
    }

    @Test
    void 缺少必要DashScope结果时不能通过() {
        GateResultPort.GateEvaluationResults results = results(false, false, Map.of("intent", new GateResultPort.MetricTotals(0.95, 0.90)));

        GateEvaluationService.GateEvaluation decision = service.evaluate("tenant-a", policy(), results);

        assertEquals(GateEvaluationService.GateStatus.FAIL, decision.status());
        assertEquals(List.of("REQUIRED_DASHSCOPE_RESULT_UNAVAILABLE"), decision.reasons());
    }

    @Test
    void 跨租户结果拒绝() {
        GateEvaluationService.GateEvaluation decision = service.evaluate("tenant-a", policy(), resultsFor("tenant-b", false, true));

        assertEquals(GateEvaluationService.GateStatus.FAIL, decision.status());
        assertEquals(List.of("TENANT_MISMATCH"), decision.reasons());
    }

    private GatePolicy policy() {
        return new GatePolicy("policy-v1", Map.of("intent", 0.05), true);
    }

    private GateResultPort.GateEvaluationResults results(boolean redline, boolean dashScopeAvailable,
            Map<String, GateResultPort.MetricTotals> metrics) {
        return new GateResultPort.GateEvaluationResults("tenant-a", UUID.randomUUID(), "manifest-sha", "baseline-sha",
                "dataset-v1", "scoring-v1", dashScopeAvailable, redline ? List.of(GateResultPort.SafetyRedline.PRIVACY_LEAK) : List.of(),
                metrics, "result-sha", Instant.parse("2026-07-28T00:00:00Z"));
    }

    private GateResultPort.GateEvaluationResults resultsFor(String tenantId, boolean redline, boolean dashScopeAvailable) {
        return new GateResultPort.GateEvaluationResults(tenantId, UUID.randomUUID(), "manifest-sha", "baseline-sha",
                "dataset-v1", "scoring-v1", dashScopeAvailable, redline ? List.of(GateResultPort.SafetyRedline.PRIVACY_LEAK) : List.of(),
                Map.of("intent", new GateResultPort.MetricTotals(0.95, 0.90)), "result-sha", Instant.now());
    }
}
