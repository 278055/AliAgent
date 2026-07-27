package com.bn.aliagent.evaluation.gate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** C 任务本地的结果读取端口；集成阶段再由适配器连接评测结果服务。 */
public interface GateResultPort {
    GateEvaluationResults read(UUID evaluationTaskId, String tenantId);

    enum SafetyRedline { PRIVACY_LEAK, FABRICATED_CRITICAL_FACT, CONFIRMATION_BYPASS, FORBIDDEN_TOOL }

    record MetricTotals(double candidateValue, double baselineValue) { }

    record GateEvaluationResults(String tenantId, UUID evaluationTaskId, String manifestDigest, String baselineDigest,
            String datasetVersion, String scoringPolicyVersion, boolean dashScopeResultAvailable,
            List<SafetyRedline> safetyRedlines, Map<String, MetricTotals> metrics, String resultDigest, Instant completedAt) {
        public GateEvaluationResults {
            safetyRedlines = List.copyOf(safetyRedlines);
            metrics = Map.copyOf(metrics);
        }
    }
}
