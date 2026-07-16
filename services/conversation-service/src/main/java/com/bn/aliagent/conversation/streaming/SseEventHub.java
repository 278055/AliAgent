package com.bn.aliagent.conversation.streaming;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class SseEventHub {
    private final Map<SseEmitter, Subscription> emitters = new ConcurrentHashMap<>();

    public void add(String tenantId, UUID conversationId, SseEmitter emitter) { emitters.put(emitter, new Subscription(tenantId, conversationId)); emitter.onCompletion(() -> emitters.remove(emitter)); emitter.onTimeout(() -> emitters.remove(emitter)); }
    public void emit(StreamingModels.StreamEvent event) {
        emitters.forEach((emitter, subscription) -> {
            if (!subscription.tenantId().equals(event.tenantId()) || !subscription.conversationId().equals(event.conversationId())) return;
            try { emitter.send(SseEmitter.event().name(event.eventType()).id(event.sequence() + ":" + event.chunkIndex()).data(event)); }
            catch (Exception ignored) { emitters.remove(emitter); }
        });
    }
    private record Subscription(String tenantId, UUID conversationId) { }
}
