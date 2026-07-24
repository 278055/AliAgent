package com.bn.aliagent.orchestration.copilot;

import java.util.List;
import java.util.UUID;

public final class CopilotPorts {
    private CopilotPorts() { }
    public record ConversationContext(UUID tenantId, UUID conversationId, String collaborationState, String assignedAgentId,
                                      List<String> messages) { }
    public interface ConversationContextPort { ConversationContext load(UUID tenantId, UUID conversationId); }
    public interface KnowledgeContextPort { List<CopilotModels.Citation> retrieve(UUID tenantId, UUID conversationId); }
    public interface CommerceFactPort { List<String> read(UUID tenantId, UUID conversationId); }
    public interface AfterSaleFactPort { List<String> read(UUID tenantId, UUID conversationId); }
    public interface CopilotModelPort { String generate(String prompt, CopilotModels.PromptContext context); }
    public interface StaffMessagePort { void send(UUID tenantId, UUID conversationId, String agentId, String content, UUID requestId); }
    public interface ConversationControlPort { ConversationContext load(UUID tenantId, UUID conversationId); }
}
