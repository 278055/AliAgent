package com.bn.aliagent.conversation.core;

import java.time.Instant;
import java.util.UUID;

public final class ConversationModels {
    private ConversationModels() { }
    public record Conversation(UUID id, String tenantId, String ownerSubjectId, String title, String status, boolean pinned, Instant createdAt, Instant updatedAt) { }
    public record Message(UUID id, String tenantId, UUID conversationId, long sequence, String senderType, String messageType, String visibility, String content, String status, UUID requestId, String metadata, Instant createdAt) { }
    public record ReplyRequest(UUID eventId, int eventVersion, String tenantId, UUID conversationId, UUID messageId,
                               UUID replyMessageId, UUID generationId, UUID requestId, String traceId, Instant occurredAt) { }
    public record Generation(UUID generationId, Message userMessage, Message aiMessage) { }
}
