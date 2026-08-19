package com.bn.aliagent.insight.query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

public record QueryPlan(String metric, AllowedTimeRange timeRange, Set<String> dimensions, Map<String, String> filters,
                        AllowedComparison comparison) {
    public QueryPlan { dimensions = Set.copyOf(dimensions); filters = Map.copyOf(filters); }
}

record AllowedTimeRange(Instant start, Instant end) {
    static AllowedTimeRange lastDays(int days) { return new AllowedTimeRange(Instant.now().minus(days, ChronoUnit.DAYS), Instant.now()); }
}
enum AllowedComparison { NONE, PREVIOUS_PERIOD }
