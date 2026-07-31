package com.bn.aliagent.insight.query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ControlledQueryExecutor {
    QueryResult execute(String trustedTenantId, QueryPlan plan, QueryActor actor);
}

interface MetricSnapshotReader { List<ResultRow> query(String tenantId, QueryPlan plan); }

final class MetricSnapshotQueryExecutor implements ControlledQueryExecutor {
    private final QueryPlanValidator validator;
    private final MetricSnapshotReader reader;
    MetricSnapshotQueryExecutor(QueryPlanValidator validator, MetricSnapshotReader reader) { this.validator = validator; this.reader = reader; }
    public QueryResult execute(String trustedTenantId, QueryPlan plan, QueryActor actor) {
        if (!trustedTenantId.equals(actor.tenantId())) throw new InvalidQueryPlanException();
        validator.validate(plan, actor);
        return new QueryResult(plan.metric(), 1, plan.timeRange().start(), plan.timeRange().end(), Instant.now(), 1,
                reader.query(trustedTenantId, plan), List.of("仅包含授权维度和已固化聚合"));
    }
}

record QueryResult(String metric, int definitionVersion, Instant periodStart, Instant periodEnd, Instant dataCutoff,
                   int aggregateRevision, List<ResultRow> rows, List<String> limitations) { }
record ResultRow(Map<String, String> dimensions, double value) { }
