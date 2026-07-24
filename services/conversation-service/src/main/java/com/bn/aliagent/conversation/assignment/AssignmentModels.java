package com.bn.aliagent.conversation.assignment;

import java.time.Instant;
import java.util.UUID;

public final class AssignmentModels {
    private AssignmentModels() { }
    public enum OfferStatus { PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED }
    public record Offer(UUID id, String tenantId, UUID queueItemId, UUID conversationId, String staffId, Instant expiresAt, OfferStatus status, UUID takeoverId) {
        public Offer withStatus(OfferStatus value, UUID valueTakeoverId) { return new Offer(id, tenantId, queueItemId, conversationId, staffId, expiresAt, value, valueTakeoverId); }
    }
    public record AssignmentResult(boolean accepted, UUID takeoverId) { }
    public record Takeover(UUID id, String tenantId, UUID conversationId, String staffId, UUID sourceOfferId) { }
}
