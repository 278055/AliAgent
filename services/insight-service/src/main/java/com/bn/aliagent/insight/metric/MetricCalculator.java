package com.bn.aliagent.insight.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class MetricCalculator {
    private final MetricCatalog catalog;
    public MetricCalculator(MetricCatalog catalog) { this.catalog = catalog; }
    public MetricValue calculate(String metric, List<MetricFact> facts) {
        MetricDefinition definition = catalog.definition(metric);
        String tenant = facts.isEmpty() ? "" : facts.get(0).tenantId();
        long numerator; long denominator; LocalDate period = period(facts);
        switch (metric) {
            case "refund_rate" -> { Map<String, MetricFact> orders = orders(facts); denominator = orders.size(); numerator = distinct(facts, f -> f.type().equals("refund") && f.state().equals("succeeded")); }
            case "refund_amount_rate" -> { Map<String, MetricFact> orders = orders(facts); denominator = orders.values().stream().mapToLong(MetricFact::amount).sum(); numerator = facts.stream().filter(f -> f.type().equals("refund") && f.state().equals("succeeded")).mapToLong(MetricFact::relatedAmount).sum(); }
            case "handoff_rate" -> { denominator = distinct(facts, f -> f.type().equals("session") && f.state().equals("completed")); numerator = distinct(facts, f -> f.type().equals("handoff") && f.state().equals("requested")); }
            case "agent_response" -> { Map<String, Instant> starts = facts.stream().filter(f -> f.type().equals("handoff") && f.state().equals("requested")).collect(Collectors.toMap(MetricFact::subjectId, MetricFact::occurredAt, Binary::earliest)); List<Long> seconds = facts.stream().filter(f -> f.type().equals("reply") && f.state().equals("human_public") && starts.containsKey(f.subjectId())).map(f -> f.occurredAt().getEpochSecond() - starts.get(f.subjectId()).getEpochSecond()).sorted().toList(); numerator = seconds.isEmpty() ? 0 : seconds.get(seconds.size() / 2); denominator = seconds.size(); }
            case "satisfaction" -> { denominator = distinct(facts, f -> f.type().equals("feedback") && (f.state().equals("positive") || f.state().equals("negative"))); numerator = distinct(facts, f -> f.type().equals("feedback") && f.state().equals("positive")); }
            case "feedback_coverage" -> { denominator = distinct(facts, f -> f.type().equals("session") && f.state().equals("completed")); numerator = distinct(facts, f -> f.type().equals("feedback") && (f.state().equals("positive") || f.state().equals("negative"))); }
            case "knowledge_gap_rate" -> { denominator = distinct(facts, f -> f.type().equals("answer") && f.state().equals("eligible")); numerator = distinct(facts, f -> f.type().equals("gap")); }
            case "logistics_issue_rate" -> { denominator = distinct(facts, f -> f.type().equals("shipment") && f.state().equals("valid")); numerator = distinct(facts, f -> f.type().equals("logistics") && f.state().equals("exception")); }
            default -> throw new IllegalArgumentException("未知指标: " + metric);
        }
        boolean noData = denominator == 0;
        BigDecimal value = noData ? null : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        return new MetricValue(tenant, metric, definition.version(), Instant.EPOCH, Instant.EPOCH, numerator, denominator, value, denominator, period, noData);
    }
    private Map<String, MetricFact> orders(List<MetricFact> facts) { return facts.stream().filter(f -> f.type().equals("order") && f.state().equals("paid")).collect(Collectors.toMap(MetricFact::subjectId, f -> f, (a, b) -> a)); }
    private long distinct(List<MetricFact> facts, Predicate<MetricFact> condition) { return facts.stream().filter(condition).map(MetricFact::subjectId).distinct().count(); }
    private LocalDate period(List<MetricFact> facts) { return facts.stream().filter(f -> f.type().equals("order") && f.state().equals("paid")).map(f -> LocalDate.ofInstant(f.occurredAt(), ZoneOffset.UTC)).min(Comparator.naturalOrder()).orElse(null); }
    private static final class Binary { private static Instant earliest(Instant a, Instant b) { return a.isBefore(b) ? a : b; } }
}
