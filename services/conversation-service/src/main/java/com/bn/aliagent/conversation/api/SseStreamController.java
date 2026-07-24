package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.streaming.SseEventHub;
import com.bn.aliagent.conversation.streaming.StreamingModels.StreamChunk;
import com.bn.aliagent.conversation.streaming.StreamingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Profile("database")
@RequestMapping
public class SseStreamController {
    private final StreamingService streaming;
    private final SseEventHub events;
    public SseStreamController(StreamingService streaming, SseEventHub events) { this.streaming = streaming; this.events = events; }
    @GetMapping(value = "/api/v1/conversations/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID conversationId, @RequestParam(defaultValue = "0") long afterSequence, HttpServletRequest request) throws Exception {
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        SseEmitter emitter = new SseEmitter(0L);
        for (var event : streaming.replay(context, conversationId, afterSequence)) emitter.send(SseEmitter.event().name(event.eventType()).id(Long.toString(event.sequence())).data(event));
        events.add(context.tenantId(), conversationId, emitter);
        return emitter;
    }
    @PostMapping("/internal/api/v1/conversations/{conversationId}/generations/{generationId}/chunks")
    public Map<String, Object> chunk(@PathVariable UUID conversationId, @PathVariable UUID generationId, @RequestBody StreamChunk body, HttpServletRequest request) {
        streaming.acceptChunk(TrustedConversationRequestContext.from(request), conversationId, generationId, body);
        return Map.of("code", 200, "message", "", "data", Map.of("accepted", true));
    }
    @PostMapping("/api/v1/conversations/{conversationId}/generations/{generationId}:cancel")
    public Map<String, Object> cancel(@PathVariable UUID conversationId, @PathVariable UUID generationId, HttpServletRequest request) {
        streaming.cancel(TrustedConversationRequestContext.from(request), conversationId, generationId);
        return Map.of("code", 200, "message", "", "data", Map.of("cancelled", true));
    }
}
