package com.bn.aliagent.insight.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricCalculatorTest {
    private final MetricCalculator calculator = new MetricCalculator(MetricCatalog.v1());

    @Test
    void refundBelongsToPaidOrderCreationPeriodAndExcludesUnpaidCancellation() {
        MetricValue result = calculator.calculate("refund_rate", List.of(
                fact("order", "o-1", "2026-06-01T10:00:00Z", "paid", 100, 0),
                fact("order", "o-2", "2026-06-01T11:00:00Z", "paid", 100, 0),
                fact("order", "o-3", "2026-06-01T12:00:00Z", "cancelled", 100, 0),
                fact("refund", "o-1", "2026-06-15T10:00:00Z", "succeeded", 0, 40)));

        assertEquals(1, result.numerator());
        assertEquals(2, result.denominator());
        assertEquals(new BigDecimal("0.5000"), result.value());
        assertEquals(LocalDate.of(2026, 6, 1), result.periodStart());
    }

    @Test
    void partialRefundsAccumulateAmountsButCountOrderOnce() {
        MetricValue result = calculator.calculate("refund_amount_rate", List.of(
                fact("order", "o-1", "2026-06-01T10:00:00Z", "paid", 100, 0),
                fact("refund", "o-1", "2026-06-02T10:00:00Z", "succeeded", 0, 25),
                fact("refund", "o-1", "2026-06-03T10:00:00Z", "succeeded", 0, 15)));

        assertEquals(40, result.numerator());
        assertEquals(100, result.denominator());
        assertEquals(new BigDecimal("0.4000"), result.value());
    }

    @Test
    void handoffAndKnowledgeGapDeduplicateAtSessionLevel() {
        MetricValue handoff = calculator.calculate("handoff_rate", List.of(
                fact("session", "s-1", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("session", "s-2", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("handoff", "s-1", "2026-06-01T10:01:00Z", "requested", 0, 0),
                fact("handoff", "s-1", "2026-06-01T10:02:00Z", "requested", 0, 0)));
        MetricValue gaps = calculator.calculate("knowledge_gap_rate", List.of(
                fact("answer", "s-1", "2026-06-01T10:00:00Z", "eligible", 0, 0),
                fact("answer", "s-2", "2026-06-01T10:00:00Z", "eligible", 0, 0),
                fact("gap", "s-1", "2026-06-01T10:00:00Z", "rag_miss", 0, 0),
                fact("gap", "s-1", "2026-06-01T10:00:00Z", "low_confidence", 0, 0)));

        assertEquals(new BigDecimal("0.5000"), handoff.value());
        assertEquals(new BigDecimal("0.5000"), gaps.value());
    }

    @Test
    void responseExcludesAiAndInternalMessagesAndSatisfactionUsesExplicitFeedbackOnly() {
        MetricValue response = calculator.calculate("agent_response", List.of(
                fact("handoff", "s-1", "2026-06-01T10:00:00Z", "requested", 0, 0),
                fact("reply", "s-1", "2026-06-01T10:01:00Z", "ai", 0, 0),
                fact("reply", "s-1", "2026-06-01T10:02:00Z", "internal", 0, 0),
                fact("reply", "s-1", "2026-06-01T10:05:00Z", "human_public", 0, 0)));
        MetricValue satisfaction = calculator.calculate("satisfaction", List.of(
                fact("session", "s-1", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("session", "s-2", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("feedback", "s-1", "2026-06-01T10:00:00Z", "positive", 0, 0)));
        MetricValue coverage = calculator.calculate("feedback_coverage", List.of(
                fact("session", "s-1", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("session", "s-2", "2026-06-01T10:00:00Z", "completed", 0, 0),
                fact("feedback", "s-1", "2026-06-01T10:00:00Z", "positive", 0, 0)));

        assertEquals(new BigDecimal("300.0000"), response.value());
        assertEquals(new BigDecimal("1.0000"), satisfaction.value());
        assertEquals(new BigDecimal("0.5000"), coverage.value());
    }

    @Test
    void returnsNoDataWhenDenominatorIsZero() {
        MetricValue result = calculator.calculate("logistics_issue_rate", List.of());
        assertTrue(result.noData());
    }

    private MetricFact fact(String type, String subjectId, String occurredAt, String state, long amount, long relatedAmount) {
        return new MetricFact("test-tenant", type, subjectId, Instant.parse(occurredAt), state, amount, relatedAmount);
    }
}
