package com.bn.aliagent.orchestration.copilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CopilotModels {
    private CopilotModels() { }
    public enum Visibility { PRIVATE }
    public enum SuggestionStatus { GENERATED, FAILED_RETRYABLE, ACCEPTED, MODIFIED, IGNORED }
    public enum ActionType { ACCEPTED, MODIFIED, IGNORED }
    public record Citation(String title, String source) { }
    public record GenerateCommand(UUID requestId, String tenantId, UUID conversationId, UUID triggerMessageId,
                                  String assignedAgentId, String currentAgentId, int refreshNo,
                                  String modelVersion, String promptVersion, String workflowVersion,
                                  UUID authorizationSnapshotId, String subjectId, String subjectType,
                                  String roles, String permissions) {
        public GenerateCommand(UUID requestId, String tenantId, UUID conversationId, UUID triggerMessageId,
                               String assignedAgentId, String currentAgentId, int refreshNo,
                               String modelVersion, String promptVersion, String workflowVersion) {
            this(requestId, tenantId, conversationId, triggerMessageId, assignedAgentId, currentAgentId, refreshNo,
                    modelVersion, promptVersion, workflowVersion, null, null, null, null, null);
        }
    }
    public record SuggestionRequested(UUID eventId, GenerateCommand command) { }
    public record PromptContext(CopilotPorts.ConversationContext conversation, List<Citation> citations,
                                List<String> commerceFacts, List<String> afterSaleFacts) { }
    public record Suggestion(UUID suggestionId, String tenantId, UUID conversationId, UUID triggerMessageId,
                             String assignedAgentId, int refreshNo, Visibility visibility, SuggestionStatus status,
                             String originalContent, String finalContent, String diffSummary, String modelVersion,
                             String promptVersion, String workflowVersion, List<Citation> citations, Instant createdAt) {
        public Suggestion withStatus(SuggestionStatus newStatus, String finalValue, String diff) {
            return new Suggestion(suggestionId, tenantId, conversationId, triggerMessageId, assignedAgentId, refreshNo,
                    visibility, newStatus, originalContent, finalValue, diff, modelVersion, promptVersion,
                    workflowVersion, citations, createdAt);
        }
    }
    public record ActionCommand(UUID requestId, String tenantId, UUID suggestionId, String agentId, String content) { }
    public record Action(UUID requestId, UUID suggestionId, ActionType type, String originalContent, String finalContent,
                         String diffSummary) { }
}
