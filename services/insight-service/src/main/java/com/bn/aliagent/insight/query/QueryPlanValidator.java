package com.bn.aliagent.insight.query;

import java.time.Duration;
import java.util.Set;

public final class QueryPlanValidator {
    private static final Set<String> METRICS = Set.of("refund_rate", "transfer_rate", "response_time", "satisfaction_rate", "knowledge_gap_rate");
    private static final Set<String> DIMENSIONS = Set.of("channel", "region", "problem_type", "day");
    private static final Set<String> FORBIDDEN = Set.of("select", "from", "join", "--", ";", "orders", "phone", "address", "identity", "(", ")");

    public void validate(QueryPlan plan, QueryActor actor) {
        String metric = plan.metric().toLowerCase();
        if (!METRICS.contains(metric) || FORBIDDEN.stream().anyMatch(metric::contains)) throw new InvalidQueryPlanException();
        if (!DIMENSIONS.containsAll(plan.dimensions()) || plan.dimensions().size() > 3) throw new InvalidQueryPlanException();
        if (plan.filters().containsKey("tenantId") || plan.filters().keySet().stream().anyMatch(key -> FORBIDDEN.stream().anyMatch(key.toLowerCase()::contains))) throw new InvalidQueryPlanException();
        if (plan.timeRange().start().isAfter(plan.timeRange().end()) || Duration.between(plan.timeRange().start(), plan.timeRange().end()).toDays() > 31) throw new InvalidQueryPlanException();
        if (actor.role() != QueryRole.OPERATOR && actor.role() != QueryRole.SUPERVISOR) throw new InvalidQueryPlanException();
    }
}

class InvalidQueryPlanException extends RuntimeException { }
