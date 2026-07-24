package com.bn.aliagent.conversation.handoff;

import java.util.UUID;

public interface HandoffRepository {
    HandoffResult findByRequest(UUID requestId);
    boolean currentStaff(String tenantId, UUID conversationId, String staffId);
    boolean conversationExists(String tenantId, UUID conversationId);
    HandoffResult complete(HandoffCommand command, String nextStatus, String takeoverStatus);
    void auditSupervisorAction(String tenantId, UUID conversationId, String supervisorId, String action, String reason);
}
