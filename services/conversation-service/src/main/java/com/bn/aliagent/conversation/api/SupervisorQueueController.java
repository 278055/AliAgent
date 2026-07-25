package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.assignment.AssignmentService;
import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/supervisor")
public class SupervisorQueueController {
    private final AssignmentService assignments;
    public SupervisorQueueController(AssignmentService assignments) { this.assignments = assignments; }
    @GetMapping("/queue") public Map<String, Object> queue(HttpServletRequest request) {
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        String roles = request.getHeader("X-User-Roles");
        if (!"STAFF".equals(context.subjectType()) || roles == null || !Set.of(roles.split(",")).contains("SUPERVISOR")) {
            throw new ConversationException("AUTH-403-001", "SUPERVISOR role is required");
        }
        return Map.of("code", 200, "message", "", "data", assignments.queueForSupervisor(context.tenantId()));
    }
    @org.springframework.web.bind.annotation.PostMapping("/queue/{queueItemId}/offer")
    public Map<String, Object> offer(@org.springframework.web.bind.annotation.PathVariable("queueItemId") UUID queueItemId, HttpServletRequest request) {
        supervisor(request);
        var value = assignments.offer(queueItemId, java.time.Instant.now());
        return Map.of("code", 200, "message", "", "data", Map.of("offerId", value.id(), "staffId", value.staffId(), "expiresAt", value.expiresAt()));
    }

    private TrustedConversationRequestContext supervisor(HttpServletRequest request) {
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        String roles = request.getHeader("X-User-Roles");
        if (!"STAFF".equals(context.subjectType()) || roles == null || !Set.of(roles.split(",")).contains("SUPERVISOR")) {
            throw new ConversationException("AUTH-403-001", "SUPERVISOR role is required");
        }
        return context;
    }
}
