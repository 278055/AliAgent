package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.assignment.AssignmentModels;
import com.bn.aliagent.conversation.assignment.AssignmentService;
import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/agent")
public class AgentQueueController {
    private final HumanQueueService queues;
    private final AssignmentService assignments;

    public AgentQueueController(HumanQueueService queues, AssignmentService assignments) { this.queues = queues; this.assignments = assignments; }

    @GetMapping("/queue")
    public Map<String, Object> queue(HttpServletRequest request) {
        TrustedConversationRequestContext context = staff(request);
        return ok(assignments.queueForStaff(context.tenantId(), context.subjectId()).stream().map(this::queueView).toList());
    }

    @PostMapping("/offers/{offerId}/accept")
    public Map<String, Object> accept(@PathVariable("offerId") UUID offerId, @RequestBody Request body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        TrustedConversationRequestContext context = staff(request);
        return ok(result(assignments.accept(context.tenantId(), offerId, context.subjectId(), requestId(body, key))));
    }

    @PostMapping("/offers/{offerId}/reject")
    public Map<String, Object> reject(@PathVariable("offerId") UUID offerId, @RequestBody Request body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        TrustedConversationRequestContext context = staff(request);
        assignments.reject(context.tenantId(), offerId, context.subjectId(), requestId(body, key));
        return ok(Map.of("rejected", true));
    }

    @PostMapping("/queue/{queueItemId}/claim")
    public Map<String, Object> claim(@PathVariable("queueItemId") UUID queueItemId, @RequestBody Request body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        TrustedConversationRequestContext context = staff(request);
        return ok(result(assignments.claim(context.tenantId(), queueItemId, context.subjectId(), requestId(body, key))));
    }

    private TrustedConversationRequestContext staff(HttpServletRequest request) {
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        if (!"STAFF".equals(context.subjectType())) throw new ConversationException("AUTH-403-001", "STAFF role is required");
        return context;
    }
    private UUID requestId(Request body, String key) {
        if (body == null || body.requestId() == null || key == null || key.isBlank()) throw new ConversationException("CONV-400-004", "requestId and Idempotency-Key are required");
        return body.requestId();
    }
    private Map<String, Object> queueView(HumanQueueModels.QueueItem item) { return Map.of("id", item.id(), "conversationId", item.conversationId(), "skillGroupId", item.skillGroupId(), "priority", item.priority(), "status", item.status().name(), "enqueuedAt", item.enqueuedAt()); }
    private Map<String, Object> result(AssignmentModels.AssignmentResult value) { return Map.of("accepted", value.accepted(), "takeoverId", value.takeoverId()); }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "message", "", "data", data); }
    public record Request(UUID requestId) { }
}
