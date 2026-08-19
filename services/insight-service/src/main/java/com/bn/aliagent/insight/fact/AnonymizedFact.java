package com.bn.aliagent.insight.fact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnonymizedFact(UUID factId, String tenantId, String type, Instant occurredAt, Map<String, String> dimensions,
        Map<String, BigDecimal> measures, Map<String, String> evidenceRefs, UUID sourceEventId, int revision,
        UUID supersedesFactId, String anonymizationRuleVersion) {
    public AnonymizedFact { dimensions = Map.copyOf(dimensions); measures = Map.copyOf(measures); evidenceRefs = Map.copyOf(evidenceRefs); }
}
