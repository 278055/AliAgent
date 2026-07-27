package com.bn.aliagent.evaluation.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GateEvaluationService {
    public GateEvaluation evaluate(String tenantId, GatePolicy policy, GateResultPort.GateEvaluationResults results) {
        if (!tenantId.equals(results.tenantId())) {
            return GateEvaluation.fail(List.of("TENANT_MISMATCH"));
        }
        if (!results.safetyRedlines().isEmpty()) {
            return GateEvaluation.fail(results.safetyRedlines().stream().map(redline -> "SAFETY_REDLINE:" + redline).toList());
        }
        if (policy.requireDashScopeResult() && !results.dashScopeResultAvailable()) {
            return GateEvaluation.fail(List.of("REQUIRED_DASHSCOPE_RESULT_UNAVAILABLE"));
        }
        List<String> reasons = relativeRegressions(policy.maximumRelativeRegression(), results.metrics());
        return reasons.isEmpty() ? GateEvaluation.pass() : GateEvaluation.fail(reasons);
    }

    private List<String> relativeRegressions(Map<String, Double> thresholds, Map<String, GateResultPort.MetricTotals> metrics) {
        List<String> reasons = new ArrayList<>();
        thresholds.forEach((metric, threshold) -> {
            GateResultPort.MetricTotals totals = metrics.get(metric);
            if (totals == null || totals.baselineValue() <= 0 || (totals.baselineValue() - totals.candidateValue()) / totals.baselineValue() > threshold) {
                reasons.add("RELATIVE_REGRESSION");
            }
        });
        return reasons;
    }

    public enum GateStatus { PASS, FAIL }

    public record GateEvaluation(GateStatus status, List<String> reasons) {
        static GateEvaluation pass() { return new GateEvaluation(GateStatus.PASS, List.of()); }
        static GateEvaluation fail(List<String> reasons) { return new GateEvaluation(GateStatus.FAIL, List.copyOf(reasons)); }
    }
}
