package com.bn.aliagent.insight.fact;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class InsightFactProjector {
    private final DeterministicFactAnonymizer anonymizer;

    public InsightFactProjector(DeterministicFactAnonymizer anonymizer) { this.anonymizer = anonymizer; }

    public AnonymizedFact project(InsightEventEnvelope event) {
        var sanitized = anonymizer.anonymize(event.payload(), event.tenantId());
        if (sanitized.quarantined()) throw new IllegalArgumentException("事件内容需要隔离");
        Map<String, String> refs = new LinkedHashMap<>();
        Map<String, BigDecimal> measures = new LinkedHashMap<>();
        if (event.payload().containsKey("orderId")) refs.put("orderId", anonymizer.anonymize(Map.of("orderId", event.payload().get("orderId")), event.tenantId())
                .canonicalJson().replaceAll("^\\{\\\"orderId\\\":\\\"|\\\"}$", ""));
        Object amount = event.payload().get("amount");
        if (amount instanceof BigDecimal value) measures.put("amount", value);
        if (amount instanceof Integer value) measures.put("amount", BigDecimal.valueOf(value));
        if (amount instanceof Long value) measures.put("amount", BigDecimal.valueOf(value));
        return new AnonymizedFact(UUID.randomUUID(), event.tenantId(), event.eventType(), event.occurredAt(), Map.of(), measures, refs,
                event.eventId(), 1, null, sanitized.ruleVersion());
    }
}
