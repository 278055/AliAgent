package com.bn.aliagent.insight.metric;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MetricValue(String tenantId, String metric, int definitionVersion, Instant windowStart,
                          Instant windowEnd, long numerator, long denominator, BigDecimal value,
                          long sampleSize, LocalDate periodStart, boolean noData) { }
