package com.bn.aliagent.insight.retention;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class InsightRetentionService {
    private final InsightRetentionPolicy policy;
    private final RetentionTarget target;
    private final Clock clock;

    public InsightRetentionService(InsightRetentionPolicy policy, RetentionTarget target, Clock clock) {
        this.policy = policy;
        this.target = target;
        this.clock = clock;
    }

    public Result purgeExpired(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new SecurityException("缺少可信租户");
        Instant cutoff = clock.instant().minus(policy.retentionDays(), ChronoUnit.DAYS);
        return new Result(target.deleteExpiredFacts(tenantId, cutoff, policy.batchSize()),
                target.deleteExpiredTopicMembers(tenantId, cutoff, policy.batchSize()));
    }

    public record Result(int deletedFacts, int deletedTopicMembers) { }
}
