package com.bn.aliagent.orchestration.insight;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record P9InsightEvent(UUID eventId, String eventType, String tenantId, String traceId, Instant occurredAt, String evidenceRef) {
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "ai.rag.completed", "ai.citation.completed", "ai.refused", "ai.tool.failed", "ai.query-plan.accepted");

    public P9InsightEvent {
        if (eventId == null || occurredAt == null || blank(eventType) || blank(tenantId) || blank(traceId) || blank(evidenceRef)) {
            throw new IllegalArgumentException("P9 AI 洞察事件字段不完整");
        }
        if (!SUPPORTED_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("unsupported P9 AI event");
        }
    }

    public Map<String, Object> envelope() {
        return Map.of("eventId", eventId.toString(), "eventType", eventType, "eventVersion", 1,
                "occurredAt", occurredAt.toString(), "tenantId", tenantId, "traceId", traceId,
                "producer", "ai-orchestration-service", "payload", Map.of("evidenceRef", evidenceRef, "signal", signal()));
    }
    private String signal() { return switch (eventType) { case "ai.rag.completed" -> "rag-completed"; case "ai.citation.completed" -> "citation-completed"; case "ai.refused" -> "refused"; case "ai.tool.failed" -> "tool-failed"; case "ai.query-plan.accepted" -> "query-plan-accepted"; default -> throw new IllegalStateException("已在构造时校验事件类型"); }; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
