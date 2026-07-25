package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationBusinessContextServiceTest {
    @Test
    void memberOwnerBindsOnlyAnOrderVerifiedForTheirTrustedIdentity() {
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        InMemoryContexts contexts = new InMemoryContexts();
        ConversationBusinessContextService service = new ConversationBusinessContextService(
                new OwnedConversationRepository(conversationId), contexts, (context, orderId) -> {
                    assertEquals("member-1", context.subjectId());
                    assertEquals(101L, orderId);
                });
        TrustedConversationRequestContext member = new TrustedConversationRequestContext("test-tenant", "member-1", "MEMBER", "trace", requestId);

        var bound = service.bind(member, conversationId, 101L, requestId);
        var replay = service.bind(member, conversationId, 101L, requestId);

        assertEquals(101L, bound.linkedOrderId());
        assertEquals(null, bound.linkedAfterSaleId());
        assertEquals(bound, replay);
        assertEquals(1, contexts.saveCount);
    }

    @Test
    void staffCannotBindAConversationBusinessContext() {
        UUID conversationId = UUID.randomUUID();
        ConversationBusinessContextService service = new ConversationBusinessContextService(
                new OwnedConversationRepository(conversationId), new InMemoryContexts(), (context, orderId) -> { });
        TrustedConversationRequestContext staff = new TrustedConversationRequestContext("test-tenant", "staff-1", "STAFF", "trace", UUID.randomUUID());

        assertThrows(ConversationException.class, () -> service.bind(staff, conversationId, 101L, UUID.randomUUID()));
    }

    @Test
    void requestIdCannotReplayAContextBoundToAnotherConversation() {
        UUID firstConversation = UUID.randomUUID();
        UUID secondConversation = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        InMemoryContexts contexts = new InMemoryContexts();
        ConversationBusinessContextService service = new ConversationBusinessContextService(
                new OwnedConversationRepository(firstConversation, secondConversation), contexts, (context, orderId) -> { });
        TrustedConversationRequestContext member = new TrustedConversationRequestContext("test-tenant", "member-1", "MEMBER", "trace", requestId);

        service.bind(member, firstConversation, 101L, requestId);

        assertThrows(ConversationException.class, () -> service.bind(member, secondConversation, 101L, requestId));
    }

    private static final class InMemoryContexts implements ConversationBusinessContextRepository {
        private ConversationBusinessContext value;
        private int saveCount;
        @Override public Optional<ConversationBusinessContext> findByRequestId(String tenantId, String subjectId, UUID requestId) {
            return value != null && value.tenantId().equals(tenantId) && value.boundBySubjectId().equals(subjectId) && value.requestId().equals(requestId) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<ConversationBusinessContext> findByConversation(String tenantId, UUID conversationId) {
            return value != null && value.tenantId().equals(tenantId) && value.conversationId().equals(conversationId) ? Optional.of(value) : Optional.empty();
        }
        @Override public ConversationBusinessContext save(ConversationBusinessContext context) { value = context; saveCount++; return context; }
    }

    private static final class OwnedConversationRepository implements ConversationRepository {
        private final java.util.Map<UUID, Conversation> conversations = new java.util.HashMap<>();
        private OwnedConversationRepository(UUID id, UUID... additional) {
            conversations.put(id, conversation(id));
            for (UUID value : additional) conversations.put(value, conversation(value));
        }
        @Override public Conversation create(Conversation value) { return value; }
        @Override public Optional<Conversation> findConversation(UUID id, String tenantId) { return Optional.ofNullable(conversations.get(id)).filter(value -> value.tenantId().equals(tenantId)); }
        @Override public java.util.List<Conversation> listConversations(String tenantId, int offset, int limit) { return java.util.List.of(); }
        @Override public long countConversations(String tenantId) { return 0; }
        @Override public Conversation update(Conversation value) { return value; }
        @Override public void softDelete(UUID id, String tenantId) { }
        @Override public Optional<ConversationModels.Message> findUserMessage(String tenantId, String subjectId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        @Override public Optional<ConversationModels.Message> findStaffMessage(String tenantId, String subjectId, UUID conversationId, UUID clientMessageId) { return Optional.empty(); }
        @Override public ConversationModels.Message appendUserMessage(ConversationModels.Message value, String subjectId) { return value; }
        @Override public ConversationModels.Message appendStaffMessage(ConversationModels.Message value, String subjectId, UUID clientMessageId) { return value; }
        @Override public ConversationModels.Message appendAiStreamingMessage(ConversationModels.Message value, UUID generationId) { return value; }
        @Override public Optional<ConversationModels.Message> findAiGeneration(String tenantId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        @Override public java.util.List<ConversationModels.Message> listMessages(String tenantId, UUID conversationId, long afterSequence, int limit) { return java.util.List.of(); }
        @Override public void enqueue(ConversationModels.ReplyRequest value) { }
        @Override public java.util.List<ConversationModels.ReplyRequest> pendingReplies(int limit) { return java.util.List.of(); }
        @Override public void markPublished(UUID eventId) { }
        private Conversation conversation(UUID id) { return new Conversation(id, "test-tenant", "member-1", "test", "HUMAN_ACTIVE", false, Instant.now(), Instant.now()); }
    }
}
