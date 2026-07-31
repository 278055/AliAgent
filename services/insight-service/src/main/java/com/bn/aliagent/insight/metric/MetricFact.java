package com.bn.aliagent.insight.metric;

import java.time.Instant;

public record MetricFact(String tenantId, String type, String subjectId, Instant occurredAt,
                         String state, long amount, long relatedAmount) { }
