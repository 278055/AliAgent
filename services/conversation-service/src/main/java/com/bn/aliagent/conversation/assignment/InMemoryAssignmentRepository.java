package com.bn.aliagent.conversation.assignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InMemoryAssignmentRepository implements AssignmentRepository {
    private final List<AssignmentModels.Offer> offers = new ArrayList<>();
    private final List<AssignmentModels.Takeover> takeovers = new ArrayList<>();
    private final Map<String, AssignmentModels.AssignmentResult> results = new HashMap<>();
    @Override public synchronized Optional<AssignmentModels.Offer> findOffer(UUID id) { return offers.stream().filter(o -> o.id().equals(id)).findFirst(); }
    @Override public synchronized void saveOffer(AssignmentModels.Offer offer) { offers.removeIf(o -> o.id().equals(offer.id())); offers.add(offer); }
    @Override public synchronized Optional<AssignmentModels.Takeover> activeTakeover(String tenant, UUID conversation) { return takeovers.stream().filter(t -> t.tenantId().equals(tenant) && t.conversationId().equals(conversation)).findFirst(); }
    @Override public synchronized AssignmentModels.Takeover createTakeoverIfAbsent(AssignmentModels.Takeover takeover) { return activeTakeover(takeover.tenantId(), takeover.conversationId()).orElseGet(() -> { takeovers.add(takeover); return takeover; }); }
    @Override public synchronized int activeTakeoversForStaff(String tenant, String staff) { return (int) takeovers.stream().filter(t -> t.tenantId().equals(tenant) && t.staffId().equals(staff)).count(); }
    @Override public synchronized Optional<AssignmentModels.AssignmentResult> findResult(String tenant, UUID requestId) { return Optional.ofNullable(results.get(tenant + ':' + requestId)); }
    @Override public synchronized void saveResult(String tenant, UUID requestId, AssignmentModels.AssignmentResult result) { results.putIfAbsent(tenant + ':' + requestId, result); }
    @Override public synchronized List<AssignmentModels.Offer> offersFor(String tenant, UUID queue) { return offers.stream().filter(o -> o.tenantId().equals(tenant) && o.queueItemId().equals(queue)).toList(); }
    public synchronized int activeTakeovers(String tenant, UUID conversation) { return (int) takeovers.stream().filter(t -> t.tenantId().equals(tenant) && t.conversationId().equals(conversation)).count(); }
}
