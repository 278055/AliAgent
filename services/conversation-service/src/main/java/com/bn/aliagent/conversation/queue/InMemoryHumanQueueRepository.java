package com.bn.aliagent.conversation.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryHumanQueueRepository implements HumanQueueRepository {
    private final List<HumanQueueModels.QueueItem> items = new ArrayList<>();
    @Override public synchronized Optional<HumanQueueModels.QueueItem> findByRequest(String tenantId, UUID requestId) { return items.stream().filter(i -> i.tenantId().equals(tenantId) && i.requestId().equals(requestId)).findFirst(); }
    @Override public synchronized Optional<HumanQueueModels.QueueItem> findActive(String tenantId, UUID conversationId) { return items.stream().filter(i -> i.tenantId().equals(tenantId) && i.conversationId().equals(conversationId) && active(i)).findFirst(); }
    @Override public synchronized Optional<HumanQueueModels.QueueItem> find(String tenantId, UUID id) { return items.stream().filter(i -> i.tenantId().equals(tenantId) && i.id().equals(id)).findFirst(); }
    @Override public synchronized Optional<HumanQueueModels.QueueItem> find(UUID id) { return items.stream().filter(i -> i.id().equals(id)).findFirst(); }
    @Override public synchronized HumanQueueModels.QueueItem createIfAbsent(HumanQueueModels.QueueItem item) { return findActive(item.tenantId(), item.conversationId()).orElseGet(() -> { items.add(item); return item; }); }
    @Override public synchronized void save(HumanQueueModels.QueueItem item) { items.replaceAll(i -> i.id().equals(item.id()) ? item : i); }
    @Override public synchronized List<HumanQueueModels.QueueItem> list(String tenantId) { return items.stream().filter(i -> i.tenantId().equals(tenantId)).sorted(Comparator.comparing(HumanQueueModels.QueueItem::priority).reversed().thenComparing(HumanQueueModels.QueueItem::enqueuedAt)).toList(); }
    public synchronized int activeCount(String tenantId, UUID conversationId) { return (int) items.stream().filter(i -> i.tenantId().equals(tenantId) && i.conversationId().equals(conversationId) && active(i)).count(); }
    private boolean active(HumanQueueModels.QueueItem item) { return item.status() == HumanQueueModels.QueueStatus.WAITING || item.status() == HumanQueueModels.QueueStatus.OFFERED || item.status() == HumanQueueModels.QueueStatus.CLAIMABLE; }
}
