package com.bn.aliagent.orchestration.messaging;

import com.bn.aliagent.orchestration.core.ReplyRequestedV2;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AiReplyRequestedV2Mapper {
    @SuppressWarnings("unchecked")
    public ReplyRequestedV2 map(Map<String, Object> event) {
        if (!Integer.valueOf(2).equals(event.get("eventVersion"))) throw new IllegalArgumentException("仅支持 ai.reply.requested.v2");
        Object payloadValue = event.get("payload");
        if (!(payloadValue instanceof Map<?, ?> rawPayload)) throw new IllegalArgumentException("payload 不能为空");
        Map<String, Object> payload = (Map<String, Object>) rawPayload;
        return new ReplyRequestedV2(uuid(event, "eventId"), text(event, "tenantId"), uuid(payload, "conversationId"),
                uuid(payload, "messageId"), uuid(payload, "replyMessageId"), uuid(payload, "generationId"),
                uuid(payload, "requestId"), text(event, "traceId"), Instant.parse(text(event, "occurredAt")));
    }

    private UUID uuid(Map<String, Object> values, String key) {
        try { return UUID.fromString(text(values, key)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(key + " 必须为 UUID", exception); }
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " 不能为空");
        return text;
    }
}
