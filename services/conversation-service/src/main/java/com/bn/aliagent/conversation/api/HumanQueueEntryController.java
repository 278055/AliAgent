package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.queue.TrustedHumanQueueEntryService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/conversations")
public class HumanQueueEntryController {
    private final TrustedHumanQueueEntryService queues;

    public HumanQueueEntryController(TrustedHumanQueueEntryService queues) { this.queues = queues; }

    @PostMapping("/{conversationId}/human-queue")
    public Map<String, Object> enqueue(@PathVariable("conversationId") UUID conversationId, @RequestBody Request body,
                                       @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        if (!"MEMBER".equals(context.subjectType())) throw new ConversationException("AUTH-403-001", "MEMBER role is required");
        if (body == null || body.requestId() == null || !body.requestId().toString().equals(key)) {
            throw new ConversationException("CONV-400-004", "Idempotency-Key must equal requestId");
        }
        var item = queues.enqueue(context.tenantId(), conversationId, body.requestId(), Instant.now());
        return Map.of("code", 200, "message", "", "data", Map.of("id", item.id(), "skillGroupId", item.skillGroupId(), "status", item.status().name()));
    }

    public record Request(UUID requestId) { }
}
