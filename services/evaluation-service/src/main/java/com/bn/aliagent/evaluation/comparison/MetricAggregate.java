package com.bn.aliagent.evaluation.comparison;

import java.util.List;

/** 聚合保留指标、标签、风险维度及失败样本证据。 */
public record MetricAggregate(String metric, String label, String riskLevel, String status, long count, List<String> evidenceRefs) {
    public MetricAggregate { evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs); }
}
