package com.bn.aliagent.evaluation.comparison;

import java.util.List;

public record VersionComparison(List<MetricDelta> metricDeltas, List<String> failedSampleEvidence) { }
