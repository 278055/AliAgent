package com.bn.aliagent.insight.retention;

import java.time.Instant;

public interface RetentionTarget {
    int deleteExpiredFacts(String tenantId, Instant before, int batchSize);
    int deleteExpiredTopicMembers(String tenantId, Instant before, int batchSize);
}
