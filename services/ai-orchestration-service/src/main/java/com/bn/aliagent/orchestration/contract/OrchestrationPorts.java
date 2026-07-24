package com.bn.aliagent.orchestration.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class OrchestrationPorts {
    private OrchestrationPorts() { }

    public interface ChatModelPort { String generate(OrchestrationContract.ExecutionContext context, String prompt); }
    public interface KnowledgeRetrievalPort { List<OrchestrationContract.Citation> retrieve(OrchestrationContract.ExecutionContext context, String query, int topK); }
    public interface MallReadToolPort { OrchestrationContract.ToolResult readOrder(OrchestrationContract.ExecutionContext context, long orderId); OrchestrationContract.ToolResult readLogistics(OrchestrationContract.ExecutionContext context, long orderId); }
    public interface ConversationStreamPort { void append(OrchestrationContract.ExecutionContext context, OrchestrationContract.StreamChunk chunk); }
    public interface VersionResolverPort { OrchestrationContract.VersionSet resolve(OrchestrationContract.ExecutionContext context); }
    public interface ExecutionRepository { Optional<OrchestrationContract.ExecutionStatus> findStatus(UUID executionId); void save(UUID executionId, OrchestrationContract.ExecutionStatus status, OrchestrationContract.VersionSet versions); }
    public interface InboxRepository { boolean claim(UUID eventId, String consumer); void complete(UUID eventId, String consumer); }
    public interface AuditPort { void audit(OrchestrationContract.ExecutionContext context, String action, String outcome); }
}
