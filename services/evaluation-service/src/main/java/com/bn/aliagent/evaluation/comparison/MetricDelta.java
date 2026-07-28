package com.bn.aliagent.evaluation.comparison;

public record MetricDelta(String metric, String label, String riskLevel, long baselineCount, long candidateCount, long delta) { }
