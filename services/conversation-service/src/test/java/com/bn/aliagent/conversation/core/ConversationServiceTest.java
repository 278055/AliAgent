package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {
    @Test
    void duplicateSubmissionReturnsPersistedMessageWithoutNewOutbox() {
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        InMemoryRepository repository = new InMemoryRepository(conversationId);
        ConversationService service = new ConversationService(repository);
        TrustedConversationRequestContext context = new TrustedConversationRequestContext("test-tenant", "test-subject", "MEMBER", UUID.randomUUID().toString(), UUID.randomUUID());

        Message first = service.submit(context, conversationId, "test-p4-a-message", requestId, requestId.toString());
        Message replay = service.submit(context, conversationId, "test-p4-a-message", requestId, requestId.toString());

        assertEquals(first.id(), replay.id());
        assertEquals(1, repository.messages.size());
        assertEquals(1, repository.outbox.size());
    }

    @Test
    void duplicateStaffClientMessageIdReturnsThePersistedHumanMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        InMemoryRepository repository = new InMemoryRepository(conversationId);
        ConversationService service = new ConversationService(repository);
        TrustedConversationRequestContext context = new TrustedConversationRequestContext("test-tenant", "test-staff", "STAFF", UUID.randomUUID().toString(), UUID.randomUUID());

        Message first = service.submitStaffMessage(context, conversationId, "test-p4-a-human", clientMessageId);
        Message replay = service.submitStaffMessage(context, conversationId, "test-p4-a-human", clientMessageId);

        assertEquals(first.id(), replay.id());
        assertEquals(1, repository.messages.size());
        assertEquals(1, first.sequence());
    }

    private static final class InMemoryRepository implements ConversationRepository {
        private final Conversation conversation;
        private final List<Message> messages = new ArrayList<>();
        private final List<ReplyRequest> outbox = new ArrayList<>();
        private InMemoryRepository(UUID id) { Instant now = Instant.now(); conversation = new Conversation(id, "test-tenant", "test-subject", "test-p4-a", "HUMAN_ACTIVE", false, now, now); }
        public Conversation create(Conversation value) { return value; }
        public Optional<Conversation> findConversation(UUID id, String tenant) { return conversation.id().equals(id) && conversation.tenantId().equals(tenant) ? Optional.of(conversation) : Optional.empty(); }
        public List<Conversation> listConversations(String tenant, int offset, int limit) { return List.of(); }
        public long countConversations(String tenant) { return 0; }
        public Conversation update(Conversation value) { return value; }
        public void softDelete(UUID id, String tenant) { }
        public Optional<Message> findUserMessage(String tenant, String subject, UUID conversation, UUID request) { return messages.stream().filter(item -> item.requestId().equals(request)).findFirst(); }
        public Optional<Message> findStaffMessage(String tenant, String subject, UUID conversation, UUID clientMessageId) { return messages.stream().filter(item -> item.senderType().equals("STAFF") && item.requestId() == null).findFirst(); }
        public Message appendUserMessage(Message value, String subject) { Message saved = new Message(value.id(), value.tenantId(), value.conversationId(), messages.size() + 1L, value.senderType(), value.messageType(), value.visibility(), value.content(), value.status(), value.requestId(), value.metadata(), value.createdAt()); messages.add(saved); return saved; }
        public Message appendStaffMessage(Message value, String subject, UUID clientMessageId) { return appendUserMessage(value, subject); }
        public List<Message> listMessages(String tenant, UUID conversation, long after, int limit) { return List.of(); }
        public void enqueue(ReplyRequest value) { outbox.add(value); }
        public List<ReplyRequest> pendingReplies(int limit) { return List.of(); }
        public void markPublished(UUID id) { }
    }
}
