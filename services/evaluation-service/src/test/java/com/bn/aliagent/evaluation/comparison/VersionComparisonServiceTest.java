package com.bn.aliagent.evaluation.comparison;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VersionComparisonServiceTest {
    @Test void 仅比较相同数据集策略模式和知识快照的结果并提供失败证据() {
        var baseline = summary("run-a", "FAIL", "sample-a");
        var candidate = summary("run-b", "FAIL", "sample-b");
        var result = new VersionComparisonService().compare(baseline, candidate);
        assertEquals(1, result.metricDeltas().size());
        assertEquals(List.of("sample-b"), result.failedSampleEvidence());
        assertThrows(IllegalArgumentException.class, () -> new VersionComparisonService().compare(baseline, mismatch()));
    }
    private static EvaluationRunSummary summary(String run, String status, String failed) { return new EvaluationRunSummary(run, UUID.randomUUID(), "dataset-v1", "score-v1", "MOCK", "knowledge-v1", List.of(new MetricAggregate("intent", "support", "HIGH", status, 1, List.of(failed)))); }
    private static EvaluationRunSummary mismatch() { return new EvaluationRunSummary("run-c", UUID.randomUUID(), "dataset-other", "score-v1", "MOCK", "knowledge-v1", List.of()); }
}
