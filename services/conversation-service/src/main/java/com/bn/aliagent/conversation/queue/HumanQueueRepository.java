package com.bn.aliagent.conversation.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HumanQueueRepository {
    Optional<HumanQueueModels.QueueItem> findByRequest(String tenantId, UUID requestId);
    Optional<HumanQueueModels.QueueItem> findActive(String tenantId, UUID conversationId);
    Optional<HumanQueueModels.QueueItem> find(String tenantId, UUID queueItemId);
    Optional<HumanQueueModels.QueueItem> find(UUID queueItemId);
    HumanQueueModels.QueueItem createIfAbsent(HumanQueueModels.QueueItem item);
    void save(HumanQueueModels.QueueItem item);
    List<HumanQueueModels.QueueItem> list(String tenantId);
}
