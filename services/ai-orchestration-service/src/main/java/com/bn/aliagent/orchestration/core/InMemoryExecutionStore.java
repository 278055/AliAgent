package com.bn.aliagent.orchestration.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InMemoryExecutionStore implements ExecutionStore {
    private final Set<UUID> inbox = new HashSet<>();
    private final Map<UUID, ExecutionRecord> records = new HashMap<>();
    private final Map<UUID, Set<String>> completedSteps = new HashMap<>();

    @Override public synchronized boolean claimInbox(UUID eventId, String consumer, String tenantId) { return inbox.add(eventId); }
    @Override public void completeInbox(UUID eventId, String consumer) { }
    @Override public synchronized boolean create(ExecutionRecord record) {
        if (findByRequestId(record.request().requestId()).isPresent()) return false;
        records.put(record.executionId(), record); return true;
    }
    @Override public synchronized Optional<ExecutionRecord> find(UUID id) { return Optional.ofNullable(records.get(id)); }
    @Override public synchronized Optional<ExecutionRecord> findByRequestId(UUID requestId) { return records.values().stream().filter(value -> value.request().requestId().equals(requestId)).findFirst(); }
    @Override public synchronized void updateStatus(UUID id, ExecutionStateMachine.Status status) { records.computeIfPresent(id, (key, value) -> value.withStatus(status)); }
    @Override public synchronized boolean isStepCompleted(UUID id, String step) { return completedSteps.getOrDefault(id, Set.of()).contains(step); }
    @Override public synchronized void markStepCompleted(UUID id, String step) { completedSteps.computeIfAbsent(id, key -> new HashSet<>()).add(step); }
    @Override public synchronized List<ExecutionRecord> findRecoverable() { return records.values().stream().filter(value -> switch (value.status()) { case CREATED, ROUTING, RUNNING, RETRY_PENDING -> true; default -> false; }).toList(); }
}
