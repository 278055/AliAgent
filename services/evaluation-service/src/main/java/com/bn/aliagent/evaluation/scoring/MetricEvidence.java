package com.bn.aliagent.evaluation.scoring;

/** 单项指标的可审计结论；红线由确定性规则固定。 */
public record MetricEvidence(String metric, MetricStatus status, String actual, String expected, String evidenceSummary, boolean safetyRedline) { }
