package com.bn.aliagent.insight.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlledQueryExecutorTest {
    @Test
    void executesOnlyForTrustedTenantAndReturnsMetricMetadata() {
        var executor = new MetricSnapshotQueryExecutor(new QueryPlanValidator(), (tenant, plan) -> List.of(new ResultRow(Map.of("channel", "web"), 0.12)));
        var result = executor.execute("test-tenant", new QueryPlan("refund_rate", AllowedTimeRange.lastDays(7), Set.of("channel"), Map.of(), AllowedComparison.NONE), new QueryActor("test-tenant", QueryRole.OPERATOR));

        assertEquals("refund_rate", result.metric());
        assertEquals(1, result.definitionVersion());
        assertEquals(1, result.rows().size());
    }
}
