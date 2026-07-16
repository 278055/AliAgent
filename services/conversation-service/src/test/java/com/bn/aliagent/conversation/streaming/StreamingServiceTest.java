package com.bn.aliagent.conversation.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationRepository;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.bn.aliagent.conversation.streaming.StreamingModels.GenerationStatus;
import com.bn.aliagent.conversation.streaming.StreamingModels.StreamChunk;
import com.bn.aliagent.conversation.streaming.StreamingModels.StreamEvent;

class StreamingServiceTest {
    private final UUID conversationId = UUID.randomUUID();
    private final UUID generationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final TrustedConversationRequestContext tenantA = new TrustedConversationRequestContext("test-p4-b-tenant-a", "subject-a", "trace-a", UUID.randomUUID());

    @Test
    void repeatedChunkIsIgnoredAndCompletedEventCanBeReplayedBySequence() {
        InMemoryStreamingRepository streams = new InMemoryStreamingRepository();
        StreamingService service = service(streams, new InMemoryDraftStore());

        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 0, "hello", false, null));
        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 0, "ignored", false, null));
        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 1, " world", true, "STOP"));

        assertEquals("hello world", streams.find(tenantA.tenantId(), conversationId, generationId).orElseThrow().content());
        assertEquals(List.of("message.completed"), service.replay(tenantA, conversationId, 6).stream().map(StreamEvent::eventType).toList());
    }

    @Test
    void cancelledGenerationCannotBeRevivedByReplayedChunk() {
        InMemoryStreamingRepository streams = new InMemoryStreamingRepository();
        StreamingService service = service(streams, new InMemoryDraftStore());
        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 0, "hello", false, null));

        service.cancel(tenantA, conversationId, generationId);
        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 1, " world", true, "STOP"));

        assertEquals(GenerationStatus.INTERRUPTED, streams.find(tenantA.tenantId(), conversationId, generationId).orElseThrow().status());
        assertEquals("hello", streams.find(tenantA.tenantId(), conversationId, generationId).orElseThrow().content());
    }

    @Test
    void foreignTenantCannotSubscribeOrWriteChunk() {
        InMemoryStreamingRepository streams = new InMemoryStreamingRepository();
        StreamingService service = service(streams, new InMemoryDraftStore());
        TrustedConversationRequestContext tenantB = new TrustedConversationRequestContext("test-p4-b-tenant-b", "subject-b", "trace-b", UUID.randomUUID());

        assertThrows(ConversationException.class, () -> service.replay(tenantB, conversationId, 0));
        assertThrows(ConversationException.class, () -> service.acceptChunk(tenantB, conversationId, generationId, new StreamChunk(messageId, 0, "x", false, null)));
    }

    @Test
    void redisFailureFallsBackToCheckpointAndRecoveryInterruptsActiveGeneration() {
        InMemoryStreamingRepository streams = new InMemoryStreamingRepository();
        StreamingService service = service(streams, new FailingDraftStore());
        service.acceptChunk(tenantA, conversationId, generationId, new StreamChunk(messageId, 0, "checkpoint", false, null));

        assertEquals("checkpoint", streams.find(tenantA.tenantId(), conversationId, generationId).orElseThrow().content());
        assertEquals(1, service.recoverInterrupted());
        assertEquals(GenerationStatus.INTERRUPTED, streams.find(tenantA.tenantId(), conversationId, generationId).orElseThrow().status());
    }

    private StreamingService service(InMemoryStreamingRepository streams, DraftStore drafts) {
        return new StreamingService(new Messages(), streams, drafts);
    }

    private final class Messages implements ConversationRepository {
        public Optional<Message> findUserMessage(String a, String b, UUID c, UUID d) { return Optional.empty(); }
        public List<Message> listMessages(String tenant, UUID id, long after, int limit) {
            if (!tenantA.tenantId().equals(tenant) || !conversationId.equals(id)) return List.of();
            return List.of(new Message(messageId, tenant, id, 7, "AI", "TEXT", "PUBLIC", "", "STREAMING", tenantA.requestId(), "{}", Instant.now()));
        }
        public com.bn.aliagent.conversation.core.ConversationModels.Conversation create(com.bn.aliagent.conversation.core.ConversationModels.Conversation value) { throw new UnsupportedOperationException(); }
        public Optional<com.bn.aliagent.conversation.core.ConversationModels.Conversation> findConversation(UUID id, String tenant) { return Optional.empty(); }
        public List<com.bn.aliagent.conversation.core.ConversationModels.Conversation> listConversations(String a, int b, int c) { return List.of(); }
        public long countConversations(String a) { return 0; }
        public com.bn.aliagent.conversation.core.ConversationModels.Conversation update(com.bn.aliagent.conversation.core.ConversationModels.Conversation value) { throw new UnsupportedOperationException(); }
        public void softDelete(UUID a, String b) { }
        public Message appendUserMessage(Message value, String subject) { throw new UnsupportedOperationException(); }
        public void enqueue(com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest value) { }
        public List<com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest> pendingReplies(int limit) { return List.of(); }
        public void markPublished(UUID id) { }
    }
}
