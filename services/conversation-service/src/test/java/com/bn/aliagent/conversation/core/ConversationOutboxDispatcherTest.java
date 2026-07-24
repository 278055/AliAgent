package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationOutboxDispatcherTest {
    @Test
    void dispatchesHistoricalV1AndNewV2OutboxEntriesWithoutChangingTheirVersion() {
        ReplyRequest v1 = new ReplyRequest(UUID.randomUUID(), 1, "test-tenant", UUID.randomUUID(), UUID.randomUUID(),
                null, null, UUID.randomUUID(), "trace-v1", Instant.now());
        ReplyRequest v2 = new ReplyRequest(UUID.randomUUID(), 2, "test-tenant", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "trace-v2", Instant.now());
        OutboxRepository repository = new OutboxRepository(v1, v2);
        List<ReplyRequest> published = new ArrayList<>();

        new ConversationOutboxDispatcher(repository, published::add).dispatchPending();

        assertEquals(List.of(1, 2), published.stream().map(ReplyRequest::eventVersion).toList());
        assertEquals(List.of(v1.eventId(), v2.eventId()), repository.published);
    }

    private static final class OutboxRepository implements ConversationRepository {
        private final List<ReplyRequest> pending;
        private final List<UUID> published = new ArrayList<>();

        private OutboxRepository(ReplyRequest... pending) { this.pending = List.of(pending); }
        public ConversationModels.Conversation create(ConversationModels.Conversation value) { throw new UnsupportedOperationException(); }
        public Optional<ConversationModels.Conversation> findConversation(UUID id, String tenantId) { return Optional.empty(); }
        public List<ConversationModels.Conversation> listConversations(String tenantId, int offset, int limit) { return List.of(); }
        public long countConversations(String tenantId) { return 0; }
        public ConversationModels.Conversation update(ConversationModels.Conversation value) { throw new UnsupportedOperationException(); }
        public void softDelete(UUID id, String tenantId) { }
        public Optional<ConversationModels.Message> findUserMessage(String tenantId, String subjectId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        public Optional<ConversationModels.Message> findStaffMessage(String tenantId, String subjectId, UUID conversationId, UUID clientMessageId) { return Optional.empty(); }
        public ConversationModels.Message appendUserMessage(ConversationModels.Message value, String subjectId) { throw new UnsupportedOperationException(); }
        public ConversationModels.Message appendStaffMessage(ConversationModels.Message value, String subjectId, UUID clientMessageId) { throw new UnsupportedOperationException(); }
        public ConversationModels.Message appendAiStreamingMessage(ConversationModels.Message value, UUID generationId) { throw new UnsupportedOperationException(); }
        public Optional<ConversationModels.Message> findAiGeneration(String tenantId, UUID conversationId, UUID requestId) { return Optional.empty(); }
        public List<ConversationModels.Message> listMessages(String tenantId, UUID conversationId, long afterSequence, int limit) { return List.of(); }
        public void enqueue(ReplyRequest value) { }
        public List<ReplyRequest> pendingReplies(int limit) { return pending; }
        public void markPublished(UUID eventId) { published.add(eventId); }
    }
}
