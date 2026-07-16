package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationApi.*;
import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.ConversationModels;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("database")
@RequestMapping("/api/v1/conversations")
public class ConversationCommandController {
    private final ConversationService service;
    public ConversationCommandController(ConversationService service) { this.service = service; }

    @PostMapping public Map<String, Object> create(@RequestBody CreateConversation body, HttpServletRequest request) { return ok(view(service.create(TrustedConversationRequestContext.from(request), body.title()))); }
    @PatchMapping("/{id}") public Map<String, Object> patch(@PathVariable UUID id, @RequestBody PatchConversation body, HttpServletRequest request) { return ok(view(service.patch(TrustedConversationRequestContext.from(request), id, body.title(), body.pinned(), body.closed()))); }
    @DeleteMapping("/{id}") public Map<String, Object> delete(@PathVariable UUID id, HttpServletRequest request) { service.delete(TrustedConversationRequestContext.from(request), id); return ok(Map.of()); }
    @PostMapping("/{id}/messages") public Map<String, Object> submit(@PathVariable UUID id, @RequestBody SubmitMessage body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        if (body.content() == null || body.content().isBlank()) throw new ConversationException("CONV-400-002", "content is required");
        var generation = service.submitWithGeneration(TrustedConversationRequestContext.from(request), id, body.content(), body.requestId(), key);
        return ok(Map.of("accepted", true, "message", messageView(generation.userMessage()), "generationId", generation.generationId(),
                "aiMessage", messageView(generation.aiMessage())));
    }
    @PostMapping("/{id}/human-messages") public Map<String, Object> staffMessage(@PathVariable UUID id, @RequestBody SubmitStaffMessage body, HttpServletRequest request) { return ok(messageView(service.submitStaffMessage(TrustedConversationRequestContext.from(request), id, body.content(), body.clientMessageId()))); }
    @PostMapping("/{id}/takeover") public Map<String, Object> takeOver(@PathVariable UUID id, HttpServletRequest request) { return ok(view(service.takeOver(TrustedConversationRequestContext.from(request), id))); }
    @PostMapping("/{id}/release") public Map<String, Object> release(@PathVariable UUID id, HttpServletRequest request) { return ok(view(service.release(TrustedConversationRequestContext.from(request), id))); }
    private ConversationView view(ConversationModels.Conversation value) { return new ConversationView(value.id(), value.title(), value.status(), value.pinned()); }
    private MessageView messageView(ConversationModels.Message value) { return new MessageView(value.id(), value.sequence(), value.content(), value.status(), value.requestId()); }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "message", "", "data", data); }
}
