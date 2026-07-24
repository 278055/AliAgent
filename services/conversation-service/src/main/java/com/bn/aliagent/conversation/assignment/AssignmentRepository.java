package com.bn.aliagent.conversation.assignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository {
    Optional<AssignmentModels.Offer> findOffer(UUID offerId);
    void saveOffer(AssignmentModels.Offer offer);
    Optional<AssignmentModels.Takeover> activeTakeover(String tenantId, UUID conversationId);
    AssignmentModels.Takeover createTakeoverIfAbsent(AssignmentModels.Takeover takeover);
    List<AssignmentModels.Offer> offersFor(String tenantId, UUID queueItemId);
}
