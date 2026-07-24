package com.bn.aliagent.conversation.realtime;

import java.time.Instant;
import java.util.UUID;

public record RealtimeEnvelope(String eventType, String tenantId, UUID conversationId, UUID requestId, Instant occurredAt,
        UUID messageId, long sequence, String sender, String content, UUID connectionId, String code, String message,
        String status) {
    public RealtimeEnvelope(String eventType, String tenantId, UUID conversationId, UUID requestId, Instant occurredAt,
            UUID messageId, long sequence, String sender, String content, UUID connectionId) {
        this(eventType, tenantId, conversationId, requestId, occurredAt, messageId, sequence, sender, content,
                connectionId, null, null, null);
    }

    public RealtimeEnvelope withConnectionId(UUID value) {
        return new RealtimeEnvelope(eventType, tenantId, conversationId, requestId, occurredAt, messageId, sequence,
                sender, content, value, code, message, status);
    }

    public static RealtimeEnvelope error(String tenantId, UUID conversationId, UUID requestId, String code,
            String message) {
        return new RealtimeEnvelope("error", tenantId, conversationId, requestId, Instant.now(), null, 0, null,
                null, null, code, message, null);
    }

    public static RealtimeEnvelope status(String tenantId, UUID conversationId, UUID requestId, String status) {
        return new RealtimeEnvelope("conversation.status", tenantId, conversationId, requestId, Instant.now(), null,
                0, null, null, null, null, null, status);
    }

    public String publicJson() {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.put("tenantId", tenantId);
        if (conversationId != null) payload.put("conversationId", conversationId);
        payload.put("requestId", requestId);
        payload.put("occurredAt", occurredAt);
        if (messageId != null) payload.put("messageId", messageId);
        if (sequence > 0) payload.put("sequence", sequence);
        if (sender != null) payload.put("sender", sender);
        if (content != null) payload.put("content", content);
        if (code != null) payload.put("code", code);
        if (message != null) payload.put("message", message);
        if (status != null) payload.put("status", status);
        try { return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(payload); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Realtime event serialization failed", exception); }
    }
}
