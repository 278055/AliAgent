package com.bn.aliagent.conversation.queue;

import java.time.Duration;
import java.util.UUID;

public final class HumanQueueService {
    private final HumanQueueRepository repository;
    private final HumanQueueModels.PriorityPolicy policy;
    public HumanQueueService(HumanQueueRepository repository, HumanQueueModels.PriorityPolicy policy) { this.repository = repository; this.policy = policy; }
    public HumanQueueModels.QueueItem enqueue(HumanQueueModels.EnqueueCommand command) {
        return repository.findByRequest(command.tenantId(), command.requestId()).orElseGet(() -> {
            var score = policy.score(new HumanQueueModels.PriorityInput(command.highRisk(), command.complaintRisk(), command.membershipLevel(), Duration.ZERO));
            return repository.createIfAbsent(new HumanQueueModels.QueueItem(UUID.randomUUID(), command.tenantId(), command.conversationId(), command.skillGroupId(), score.score(), "p7-routing-v1", score.ruleVersion(), command.enqueuedAt(), command.requestId(), 0, HumanQueueModels.QueueStatus.WAITING));
        });
    }
    public HumanQueueModels.QueueItem require(String tenantId, UUID queueItemId) { return repository.find(tenantId, queueItemId).orElseThrow(() -> new IllegalArgumentException("queue item is not in tenant")); }
    public HumanQueueModels.QueueItem require(UUID queueItemId) { return repository.find(queueItemId).orElseThrow(() -> new IllegalArgumentException("queue item not found")); }
    public java.util.List<HumanQueueModels.QueueItem> list(String tenantId) { return repository.list(tenantId); }
    public void save(HumanQueueModels.QueueItem item) { repository.save(item); }
}
