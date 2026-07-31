package com.bn.aliagent.insight.metric;

import java.util.Set;

public record MetricDefinition(String metric, int version, TimeSemantics timeSemantics,
                               Set<String> allowedDimensions, int minimumSampleSize) {
    public MetricDefinition { allowedDimensions = Set.copyOf(allowedDimensions); }
    public enum TimeSemantics { ORDER_CREATED, EVENT_OCCURRED, SESSION_COMPLETED }
}
