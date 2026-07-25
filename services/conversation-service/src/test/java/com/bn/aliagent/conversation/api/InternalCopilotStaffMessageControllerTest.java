package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.core.ConversationModels;
import com.bn.aliagent.conversation.core.ConversationRepository;
import com.bn.aliagent.conversation.core.ConversationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InternalCopilotStaffMessageControllerTest {
    @Test
    void writesTheAgentMessageUsingTheActionRequestIdAsClientId() {
        UUID conversationId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        Messages repository = new Messages(conversationId);
        var controller = new InternalCopilotStaffMessageController(new ConversationService(repository));
        MockHttpServletRequest request = trustedRequest("staff-1");

        var data = controller.send(conversationId, new InternalCopilotStaffMessageController.Body("test-suggestion", clientMessageId), request);

        assertEquals("test-suggestion", ((java.util.Map<?, ?>) data.get("data")).get("content"));
        assertEquals(clientMessageId, repository.clientMessageId);
    }

    private static MockHttpServletRequest trustedRequest(String subjectId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", true);
        request.addHeader("X-Tenant-Id", "test-tenant");
        request.addHeader("X-Subject-Id", subjectId);
        request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-Trace-Id", "test-trace");
        request.addHeader("X-Request-Id", UUID.randomUUID().toString());
        return request;
    }

    private static final class Messages implements ConversationRepository {
        private final ConversationModels.Conversation conversation;
        private UUID clientMessageId;
        private Messages(UUID conversationId) { conversation = new ConversationModels.Conversation(conversationId, "test-tenant", "member-1", "test", "HUMAN_ACTIVE", false, Instant.now(), Instant.now()); }
        @Override public ConversationModels.Conversation create(ConversationModels.Conversation value) { return value; }
        @Override public Optional<ConversationModels.Conversation> findConversation(UUID id, String tenantId) { return conversation.id().equals(id) && conversation.tenantId().equals(tenantId) ? Optional.of(conversation) : Optional.empty(); }
        @Override public List<ConversationModels.Conversation> listConversations(String tenantId, int offset, int limit) { return List.of(); }
        @Override public long countConversations(String tenantId) { return 0; }
        @Override public ConversationModels.Conversation update(ConversationModels.Conversation value) { return value; }
        @Override public void softDelete(UUID id, String tenantId) { }
        @Override public Optional<ConversationModels.Message> findUserMessage(String tenantId, String subjectId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        @Override public Optional<ConversationModels.Message> findStaffMessage(String tenantId, String subjectId, UUID conversationId, UUID clientId) { return Optional.empty(); }
        @Override public ConversationModels.Message appendUserMessage(ConversationModels.Message value, String subjectId) { return value; }
        @Override public ConversationModels.Message appendStaffMessage(ConversationModels.Message value, String subjectId, UUID clientId) { clientMessageId = clientId; return value; }
        @Override public ConversationModels.Message appendAiStreamingMessage(ConversationModels.Message value, UUID generationId) { return value; }
        @Override public Optional<ConversationModels.Message> findAiGeneration(String tenantId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        @Override public List<ConversationModels.Message> listMessages(String tenantId, UUID conversationId, long afterSequence, int limit) { return List.of(); }
        @Override public void enqueue(ConversationModels.ReplyRequest value) { }
        @Override public List<ConversationModels.ReplyRequest> pendingReplies(int limit) { return List.of(); }
        @Override public void markPublished(UUID eventId) { }
    }
}
