package com.bn.aliagent.evaluation.comparison;

import java.util.ArrayList;
import java.util.List;

public final class VersionComparisonService {
    public VersionComparison compare(EvaluationRunSummary baseline, EvaluationRunSummary candidate) {
        sameConditions(baseline, candidate);
        List<MetricDelta> deltas = new ArrayList<>();
        for (MetricAggregate base : baseline.metrics()) {
            MetricAggregate current = candidate.metrics().stream().filter(value -> sameDimension(base, value)).findFirst()
                    .orElse(new MetricAggregate(base.metric(), base.label(), base.riskLevel(), "NOT_APPLICABLE", 0, List.of()));
            deltas.add(new MetricDelta(base.metric(), base.label(), base.riskLevel(), base.count(), current.count(), current.count() - base.count()));
        }
        List<String> failures = candidate.metrics().stream().filter(value -> "FAIL".equals(value.status())).flatMap(value -> value.evidenceRefs().stream()).toList();
        return new VersionComparison(List.copyOf(deltas), failures);
    }
    private void sameConditions(EvaluationRunSummary left, EvaluationRunSummary right) {
        if (!left.datasetVersion().equals(right.datasetVersion()) || !left.scoringPolicyVersion().equals(right.scoringPolicyVersion())
                || !left.executionMode().equals(right.executionMode()) || !left.knowledgeSnapshot().equals(right.knowledgeSnapshot())) {
            throw new IllegalArgumentException("仅允许比较相同数据集、评分策略、执行模式和知识快照");
        }
    }
    private boolean sameDimension(MetricAggregate left, MetricAggregate right) { return left.metric().equals(right.metric()) && left.label().equals(right.label()) && left.riskLevel().equals(right.riskLevel()); }
}
