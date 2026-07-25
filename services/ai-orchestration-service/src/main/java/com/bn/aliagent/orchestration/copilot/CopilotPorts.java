package com.bn.aliagent.orchestration.copilot;

import java.util.List;
import java.util.UUID;

public final class CopilotPorts {
    private CopilotPorts() { }
    public record ConversationContext(String tenantId, UUID conversationId, String collaborationState, String assignedAgentId,
                                      List<String> messages, Long linkedOrderId, String linkedAfterSaleId) {
        public ConversationContext(String tenantId, UUID conversationId, String collaborationState, String assignedAgentId,
                                   List<String> messages) {
            this(tenantId, conversationId, collaborationState, assignedAgentId, messages, null, null);
        }
    }
    public interface ConversationContextPort { ConversationContext load(String tenantId, UUID conversationId); }
    public interface KnowledgeContextPort {
        List<CopilotModels.Citation> retrieve(String tenantId, UUID conversationId);
        default List<CopilotModels.Citation> retrieve(String tenantId, UUID conversationId, UUID authorizationSnapshotId,
                String subjectId, String subjectType, String roles, String permissions, String query) {
            return retrieve(tenantId, conversationId);
        }
    }
    public interface CommerceFactPort { List<String> read(String tenantId, UUID conversationId); }
    public interface AfterSaleFactPort { List<String> read(String tenantId, UUID conversationId); }
    public interface CopilotModelPort { String generate(String prompt, CopilotModels.PromptContext context); }
    public interface StaffMessagePort { void send(String tenantId, UUID conversationId, String agentId, String content, UUID requestId); }
    public interface ConversationControlPort { ConversationContext load(String tenantId, UUID conversationId); }
}
