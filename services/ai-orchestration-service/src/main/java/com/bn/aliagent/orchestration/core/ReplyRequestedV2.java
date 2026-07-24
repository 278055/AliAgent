package com.bn.aliagent.orchestration.core;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReplyRequestedV2(UUID eventId, String tenantId, UUID conversationId, UUID messageId,
                               UUID replyMessageId, UUID generationId, UUID requestId, String traceId,
                               Instant occurredAt) {
    public ReplyRequestedV2 {
        Objects.requireNonNull(eventId, "eventId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(conversationId, "conversationId 不能为空");
        Objects.requireNonNull(messageId, "messageId 不能为空");
        Objects.requireNonNull(replyMessageId, "replyMessageId 不能为空");
        Objects.requireNonNull(generationId, "generationId 不能为空");
        Objects.requireNonNull(requestId, "requestId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(occurredAt, "occurredAt 不能为空");
    }
}
