package com.bn.aliagent.insight.radar; import java.math.BigDecimal;
public record ThresholdRule(String metric,int version,BigDecimal absoluteThreshold,BigDecimal trendMultiplier,int minimumSampleSize,RiskLevel riskLevel) { }
