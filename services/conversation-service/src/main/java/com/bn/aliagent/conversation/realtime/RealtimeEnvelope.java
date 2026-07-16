package com.bn.aliagent.conversation.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEnvelope(String eventType, String tenantId, UUID conversationId, UUID requestId, Instant occurredAt,
        UUID messageId, long sequence, String sender, String content, UUID connectionId) {
    public RealtimeEnvelope withConnectionId(UUID value) {
        return new RealtimeEnvelope(eventType, tenantId, conversationId, requestId, occurredAt, messageId, sequence, sender, content, value);
    }
}
