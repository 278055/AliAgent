package com.bn.aliagent.conversation.transfer;

import java.util.UUID;

public interface TransferRepository {
    String currentAgent(String tenantId, UUID conversationId);
    boolean conversationExists(String tenantId, UUID conversationId);
    boolean targetEligible(String tenantId, String staffId);
    Transfer findByRequest(String tenantId, UUID requestId);
    Transfer save(Transfer transfer);
    Transfer find(UUID id, String tenantId);
    Transfer acceptAndReplaceIfPending(Transfer transfer, UUID requestId);
    Transfer requeueAndTransferAtomically(SkillGroupTransferCommand command);
    void auditSupervisorAction(String tenantId, UUID conversationId, String supervisorId, String action, String reason);
}
