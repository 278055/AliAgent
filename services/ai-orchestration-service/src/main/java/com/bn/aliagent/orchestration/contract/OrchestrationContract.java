package com.bn.aliagent.orchestration.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrchestrationContract {
    private OrchestrationContract() { }

    public enum IntentType { GENERAL, KNOWLEDGE, ORDER, LOGISTICS, HUMAN_HANDOFF }
    public enum WorkflowType { GENERAL_ANSWER, RAG_ANSWER, ORDER_READ, LOGISTICS_READ, HUMAN_HANDOFF }
    public enum ExecutionStatus { RECEIVED, ROUTED, RUNNING, COMPLETED, FAILED, HANDOFF_REQUESTED, DEGRADED }
    public enum ToolType { READ, WRITE }
    public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
    public enum DegradationReason { MODEL_UNAVAILABLE, KNOWLEDGE_UNAVAILABLE, MALL_UNAVAILABLE, RATE_LIMITED, QUOTA_EXHAUSTED, DUPLICATE_EVENT, INVALID_EVENT }

    public record ExecutionContext(String tenantId, String subjectId, String subjectType, List<String> roles,
                                   List<String> permissions, String traceId, UUID requestId, UUID conversationId,
                                   UUID sourceMessageId, UUID replyMessageId, UUID generationId,
                                   UUID authorizationSnapshotId) { }
    public record ReplyRequest(UUID eventId, int eventVersion, ExecutionContext context, Instant occurredAt) { }
    public record Citation(UUID documentId, UUID versionId, UUID chunkId, String content, double score) { }
    public record StreamChunk(UUID messageId, UUID generationId, int chunkIndex, String content, boolean completed,
                              String finishReason, List<Citation> citations) { }
    public record VersionSet(String promptVersion, String workflowVersion, String modelVersion, String ruleVersion,
                             List<String> knowledgeVersionIds) { }
    public record ToolResult(String toolName, Map<String, Object> data, List<Citation> citations) { }
}
