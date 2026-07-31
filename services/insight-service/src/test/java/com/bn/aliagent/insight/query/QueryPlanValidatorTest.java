package com.bn.aliagent.insight.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryPlanValidatorTest {
    private final QueryPlanValidator validator = new QueryPlanValidator();
    private final QueryActor operator = new QueryActor("test-tenant", QueryRole.OPERATOR);

    @Test
    void acceptsWhitelistedRefundRatePlan() {
        assertDoesNotThrow(() -> validator.validate(new QueryPlan("refund_rate", new AllowedTimeRange(Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-07T00:00:00Z")), Set.of("channel"), Map.of(), AllowedComparison.NONE), operator));
    }

    @Test
    void rejectsSqlSensitiveDimensionAndTenantMismatch() {
        assertThrows(InvalidQueryPlanException.class, () -> validator.validate(new QueryPlan("refund_rate; SELECT * FROM orders", AllowedTimeRange.lastDays(7), Set.of(), Map.of(), AllowedComparison.NONE), operator));
        assertThrows(InvalidQueryPlanException.class, () -> validator.validate(new QueryPlan("refund_rate", AllowedTimeRange.lastDays(7), Set.of("phone"), Map.of(), AllowedComparison.NONE), operator));
        assertThrows(InvalidQueryPlanException.class, () -> validator.validate(new QueryPlan("refund_rate", AllowedTimeRange.lastDays(7), Set.of(), Map.of("tenantId", "other"), AllowedComparison.NONE), operator));
    }
}
