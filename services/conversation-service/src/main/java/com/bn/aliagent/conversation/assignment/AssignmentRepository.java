package com.bn.aliagent.conversation.assignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository {
    Optional<AssignmentModels.Offer> findOffer(UUID offerId);
    void saveOffer(AssignmentModels.Offer offer);
    Optional<AssignmentModels.Takeover> activeTakeover(String tenantId, UUID conversationId);
    AssignmentModels.Takeover createTakeoverIfAbsent(AssignmentModels.Takeover takeover);
    int activeTakeoversForStaff(String tenantId, String staffId);
    Optional<AssignmentModels.AssignmentResult> findResult(String tenantId, UUID requestId);
    void saveResult(String tenantId, UUID requestId, AssignmentModels.AssignmentResult result);
    List<AssignmentModels.Offer> offersFor(String tenantId, UUID queueItemId);
}
