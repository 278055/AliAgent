package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.queue.VerifiedConversationTagRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/internal/api/v1/conversations")
public class InternalVerifiedTagController {
    private final VerifiedConversationTagRepository tags;
    public InternalVerifiedTagController(VerifiedConversationTagRepository tags) { this.tags = tags; }
    @PostMapping("/{conversationId}/verified-tags")
    public Map<String, Object> verify(@PathVariable("conversationId") UUID conversationId, @RequestBody Request body, HttpServletRequest request) {
        var context = TrustedConversationRequestContext.from(request);
        if (body == null || body.tagCode() == null || body.tagCode().isBlank() || !Set.of("RULE_ENGINE", "AFTERSALE_VERIFIED", "ORDER_VERIFIED").contains(body.source())) throw new IllegalArgumentException("可信标签参数无效");
        tags.verify(context.tenantId(), conversationId, body.tagCode(), body.source(), Instant.now());
        return Map.of("code", 200, "message", "", "data", Map.of("tagCode", body.tagCode()));
    }
    public record Request(String tagCode, String source) { }
}
