package com.bn.aliagent.insight.metric;

import java.util.Map;
import java.util.Set;

public final class MetricCatalog {
    private final Map<String, MetricDefinition> definitions;
    private MetricCatalog(Map<String, MetricDefinition> definitions) { this.definitions = Map.copyOf(definitions); }
    public static MetricCatalog v1() {
        Set<String> dimensions = Set.of("category", "channel", "reason");
        return new MetricCatalog(Map.of(
                "refund_rate", definition("refund_rate", MetricDefinition.TimeSemantics.ORDER_CREATED, dimensions),
                "refund_amount_rate", definition("refund_amount_rate", MetricDefinition.TimeSemantics.ORDER_CREATED, dimensions),
                "handoff_rate", definition("handoff_rate", MetricDefinition.TimeSemantics.SESSION_COMPLETED, Set.of("channel")),
                "agent_response", definition("agent_response", MetricDefinition.TimeSemantics.EVENT_OCCURRED, Set.of("channel")),
                "satisfaction", definition("satisfaction", MetricDefinition.TimeSemantics.SESSION_COMPLETED, Set.of("channel")),
                "feedback_coverage", definition("feedback_coverage", MetricDefinition.TimeSemantics.SESSION_COMPLETED, Set.of("channel")),
                "knowledge_gap_rate", definition("knowledge_gap_rate", MetricDefinition.TimeSemantics.SESSION_COMPLETED, Set.of("channel", "reason")),
                "logistics_issue_rate", definition("logistics_issue_rate", MetricDefinition.TimeSemantics.EVENT_OCCURRED, Set.of("category", "reason"))));
    }
    private static MetricDefinition definition(String name, MetricDefinition.TimeSemantics time, Set<String> dimensions) {
        return new MetricDefinition(name, 1, time, dimensions, 10);
    }
    public MetricDefinition definition(String metric) {
        MetricDefinition definition = definitions.get(metric);
        if (definition == null) throw new IllegalArgumentException("未知指标: " + metric);
        return definition;
    }
}
