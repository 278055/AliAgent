package com.bn.aliagent.conversation.messaging;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将会话领域事件投影为不含消息正文的 P9 信封。 */
final class P9InsightEventMapper {
    private P9InsightEventMapper() { }

    static Map<String, Object> map(HumanCollaborationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceRef", "conversation-" + event.conversationId());
        payload.put("signal", signal(event.eventType()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("tenantId", event.tenantId());
        envelope.put("traceId", event.requestId().toString());
        envelope.put("producer", "conversation-service");
        envelope.put("payload", payload);
        return Map.copyOf(envelope);
    }

    private static String signal(String eventType) {
        return switch (eventType) {
            case "conversation.completed" -> "completed";
            case "conversation.feedback.received" -> "explicit-feedback";
            case "conversation.human.requested" -> "human-requested";
            case "conversation.human.first-public-reply" -> "first-human-public-reply";
            default -> throw new IllegalArgumentException("unsupported P9 conversation event: " + eventType);
        };
    }
}
