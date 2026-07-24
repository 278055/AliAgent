package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
public class InternalCopilotStaffMessageController {
    private final ConversationService conversations;

    public InternalCopilotStaffMessageController(ConversationService conversations) { this.conversations = conversations; }

    @PostMapping("/{conversationId}/staff-messages")
    public Map<String, Object> send(@PathVariable UUID conversationId, @RequestBody Body body, HttpServletRequest request) {
        if (body.content() == null || body.content().isBlank() || body.clientMessageId() == null) {
            throw new ConversationException("CONV-400-002", "content and clientMessageId are required");
        }
        var message = conversations.submitStaffMessage(TrustedConversationRequestContext.from(request), conversationId,
                body.content(), body.clientMessageId());
        return Map.of("code", 200, "message", "", "data", Map.of("id", message.id(), "content", message.content(), "sequence", message.sequence()));
    }

    public record Body(String content, UUID clientMessageId) { }
}
