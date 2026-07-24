package com.bn.aliagent.orchestration.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionStore {
    boolean claimInbox(UUID eventId, String consumer, String tenantId);
    void completeInbox(UUID eventId, String consumer);
    boolean create(ExecutionRecord record);
    Optional<ExecutionRecord> find(UUID executionId);
    Optional<ExecutionRecord> findByRequestId(UUID requestId);
    void updateStatus(UUID executionId, ExecutionStateMachine.Status status);
    boolean isStepCompleted(UUID executionId, String step);
    void markStepCompleted(UUID executionId, String step);
    List<ExecutionRecord> findRecoverable();
}
