package com.bn.aliagent.conversation.assignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryAssignmentRepository implements AssignmentRepository {
    private final List<AssignmentModels.Offer> offers = new ArrayList<>();
    private final List<AssignmentModels.Takeover> takeovers = new ArrayList<>();
    @Override public synchronized Optional<AssignmentModels.Offer> findOffer(UUID id) { return offers.stream().filter(o -> o.id().equals(id)).findFirst(); }
    @Override public synchronized void saveOffer(AssignmentModels.Offer offer) { offers.removeIf(o -> o.id().equals(offer.id())); offers.add(offer); }
    @Override public synchronized Optional<AssignmentModels.Takeover> activeTakeover(String tenant, UUID conversation) { return takeovers.stream().filter(t -> t.tenantId().equals(tenant) && t.conversationId().equals(conversation)).findFirst(); }
    @Override public synchronized AssignmentModels.Takeover createTakeoverIfAbsent(AssignmentModels.Takeover takeover) { return activeTakeover(takeover.tenantId(), takeover.conversationId()).orElseGet(() -> { takeovers.add(takeover); return takeover; }); }
    @Override public synchronized List<AssignmentModels.Offer> offersFor(String tenant, UUID queue) { return offers.stream().filter(o -> o.tenantId().equals(tenant) && o.queueItemId().equals(queue)).toList(); }
    public synchronized int activeTakeovers(String tenant, UUID conversation) { return (int) takeovers.stream().filter(t -> t.tenantId().equals(tenant) && t.conversationId().equals(conversation)).count(); }
}
