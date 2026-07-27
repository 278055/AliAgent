package com.bn.aliagent.evaluation.comparison;

import java.util.List;
import java.util.UUID;

public record EvaluationRunSummary(String runId, UUID manifestId, String datasetVersion, String scoringPolicyVersion,
                                   String executionMode, String knowledgeSnapshot, List<MetricAggregate> metrics) {
    public EvaluationRunSummary { metrics = metrics == null ? List.of() : List.copyOf(metrics); }
}
