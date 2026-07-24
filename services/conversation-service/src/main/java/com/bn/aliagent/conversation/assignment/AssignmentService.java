package com.bn.aliagent.conversation.assignment;

import com.bn.aliagent.conversation.agent.AgentModels;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class AssignmentService {
    private final HumanQueueService queues; private final AssignmentPorts directory; private final AssignmentRepository repository;
    private final int timeoutSeconds; private final int maxAttempts;
    public AssignmentService(HumanQueueService queues, AssignmentPorts directory, AssignmentRepository repository, int timeoutSeconds, int maxAttempts) { this.queues = queues; this.directory = directory; this.repository = repository; this.timeoutSeconds = timeoutSeconds; this.maxAttempts = maxAttempts; }
    public synchronized AssignmentModels.Offer offer(UUID queueItemId, Instant now) {
        HumanQueueModels.QueueItem item = findQueue(queueItemId);
        if (item.status() != HumanQueueModels.QueueStatus.WAITING) throw new IllegalStateException("queue item is not waiting");
        AgentModels.AgentSnapshot agent = directory.candidates(item.tenantId(), item.skillGroupId()).stream().filter(AgentModels.AgentSnapshot::membershipEnabled).filter(AgentModels.AgentSnapshot::online).filter(AgentModels.AgentSnapshot::hasCapacity).sorted(Comparator.comparingInt(AgentModels.AgentSnapshot::activeCount).thenComparing(AgentModels.AgentSnapshot::lastAssignedAt, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(AgentModels.AgentSnapshot::staffId)).findFirst().orElseThrow(() -> new IllegalStateException("no eligible agent"));
        AssignmentModels.Offer offer = new AssignmentModels.Offer(UUID.randomUUID(), item.tenantId(), item.id(), item.conversationId(), agent.staffId(), now.plusSeconds(timeoutSeconds), AssignmentModels.OfferStatus.PENDING, null);
        repository.saveOffer(offer); saveQueue(item.nextAttempt(HumanQueueModels.QueueStatus.OFFERED)); return offer;
    }
    public synchronized AssignmentModels.AssignmentResult accept(String tenantId, UUID offerId, String staffId, UUID requestId) { return accept(tenantId, offerId, staffId, requestId, Instant.now()); }
    public synchronized AssignmentModels.AssignmentResult accept(String tenantId, UUID offerId, String staffId, UUID requestId, Instant now) {
        var repeated = repository.findResult(tenantId, requestId);
        if (repeated.isPresent()) return repeated.get();
        AssignmentModels.AssignmentResult result = complete(tenantId, offerId, staffId, now);
        repository.saveResult(tenantId, requestId, result);
        return result;
    }
    public synchronized void reject(String tenantId, UUID offerId, String staffId, UUID requestId) {
        if (repository.findResult(tenantId, requestId).isPresent()) return;
        AssignmentModels.Offer offer = repository.findOffer(offerId).orElseThrow(() -> new IllegalArgumentException("offer not found"));
        if (!offer.tenantId().equals(tenantId) || !offer.staffId().equals(staffId) || offer.status() != AssignmentModels.OfferStatus.PENDING) throw new IllegalArgumentException("offer is not rejectable");
        repository.saveOffer(offer.withStatus(AssignmentModels.OfferStatus.REJECTED, null));
        HumanQueueModels.QueueItem item = queues.require(tenantId, offer.queueItemId());
        saveQueue(item.assignmentAttempts() >= maxAttempts ? item.withStatus(HumanQueueModels.QueueStatus.CLAIMABLE) : item.withStatus(HumanQueueModels.QueueStatus.WAITING));
        repository.saveResult(tenantId, requestId, new AssignmentModels.AssignmentResult(false, null));
    }
    public synchronized void expire(UUID offerId, Instant now) {
        AssignmentModels.Offer offer = repository.findOffer(offerId).orElseThrow(() -> new IllegalArgumentException("offer not found"));
        if (offer.status() != AssignmentModels.OfferStatus.PENDING || now.isBefore(offer.expiresAt())) return;
        repository.saveOffer(offer.withStatus(AssignmentModels.OfferStatus.EXPIRED, null));
        HumanQueueModels.QueueItem item = queues.require(offer.tenantId(), offer.queueItemId());
        saveQueue(item.assignmentAttempts() >= maxAttempts ? item.withStatus(HumanQueueModels.QueueStatus.CLAIMABLE) : item.withStatus(HumanQueueModels.QueueStatus.WAITING));
    }
    public synchronized AssignmentModels.AssignmentResult claim(String tenantId, UUID queueItemId, String staffId, UUID requestId) {
        var repeated = repository.findResult(tenantId, requestId);
        if (repeated.isPresent()) return repeated.get();
        HumanQueueModels.QueueItem item = queues.require(tenantId, queueItemId);
        var existing = repository.activeTakeover(tenantId, item.conversationId());
        if (existing.isPresent()) return new AssignmentModels.AssignmentResult(existing.get().staffId().equals(staffId), existing.get().id());
        if (item.status() != HumanQueueModels.QueueStatus.CLAIMABLE) throw new IllegalStateException("queue item is not claimable");
        boolean allowed = directory.candidates(tenantId, item.skillGroupId()).stream().anyMatch(a -> a.staffId().equals(staffId) && a.membershipEnabled() && a.online() && a.hasCapacity());
        if (!allowed) throw new IllegalArgumentException("staff is not eligible");
        AssignmentModels.AssignmentResult result = establish(item, staffId, null);
        repository.saveResult(tenantId, requestId, result);
        return result;
    }
    public List<HumanQueueModels.QueueItem> queueForStaff(String tenantId, String staffId) { return list(tenantId).stream().filter(i -> directory.candidates(tenantId, i.skillGroupId()).stream().anyMatch(a -> a.staffId().equals(staffId) && a.membershipEnabled())).toList(); }
    public List<HumanQueueModels.QueueItem> queueForSupervisor(String tenantId) { return list(tenantId); }
    private AssignmentModels.AssignmentResult complete(String tenant, UUID offerId, String staff, Instant now) {
        AssignmentModels.Offer offer = repository.findOffer(offerId).orElseThrow(() -> new IllegalArgumentException("offer not found"));
        if (!offer.tenantId().equals(tenant) || !offer.staffId().equals(staff)) throw new IllegalArgumentException("offer is not accessible");
        if (!now.isBefore(offer.expiresAt())) throw new IllegalStateException("offer has expired");
        if (offer.status() == AssignmentModels.OfferStatus.ACCEPTED) return new AssignmentModels.AssignmentResult(false, offer.takeoverId());
        if (offer.status() != AssignmentModels.OfferStatus.PENDING) return new AssignmentModels.AssignmentResult(false, offer.takeoverId());
        return establish(queues.require(tenant, offer.queueItemId()), staff, offer);
    }
    private AssignmentModels.AssignmentResult establish(HumanQueueModels.QueueItem item, String staffId, AssignmentModels.Offer offer) {
        boolean hasCapacity = directory.candidates(item.tenantId(), item.skillGroupId()).stream()
                .filter(agent -> agent.staffId().equals(staffId))
                .anyMatch(agent -> agent.membershipEnabled() && agent.online() && agent.activeCount() + repository.activeTakeoversForStaff(item.tenantId(), staffId) < agent.maxConcurrent());
        if (!hasCapacity) throw new IllegalStateException("staff capacity is exhausted");
        AssignmentModels.Takeover takeover = repository.createTakeoverIfAbsent(new AssignmentModels.Takeover(UUID.randomUUID(), item.tenantId(), item.conversationId(), staffId, offer == null ? null : offer.id()));
        boolean accepted = takeover.staffId().equals(staffId); if (offer != null) repository.saveOffer(offer.withStatus(AssignmentModels.OfferStatus.ACCEPTED, takeover.id()));
        if (accepted) saveQueue(item.withStatus(HumanQueueModels.QueueStatus.ASSIGNED));
        return new AssignmentModels.AssignmentResult(accepted, takeover.id());
    }
    private HumanQueueModels.QueueItem findQueue(UUID id) { return queues.require(id); }
    private List<HumanQueueModels.QueueItem> list(String tenant) { return queues.list(tenant); }
    private void saveQueue(HumanQueueModels.QueueItem item) { queues.save(item); }
}
