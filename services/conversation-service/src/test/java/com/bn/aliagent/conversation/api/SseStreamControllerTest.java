package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationRepository;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.streaming.DraftStore;
import com.bn.aliagent.conversation.streaming.SseEventHub;
import com.bn.aliagent.conversation.streaming.StreamingModels.StreamChunk;
import com.bn.aliagent.conversation.streaming.StreamingRepository;
import com.bn.aliagent.conversation.streaming.StreamingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SseStreamControllerTest {
    @Test
    void streamReplaysCompletedMessageAfterRequestedSequence() throws Exception {
        UUID conversation = UUID.randomUUID(); UUID message = UUID.randomUUID(); UUID generation = UUID.randomUUID();
        TrustedConversationRequestContext context = new TrustedConversationRequestContext("test-p4-b-tenant", "subject", "trace", UUID.randomUUID());
        StreamingService service = new StreamingService(new Messages(conversation, message, context), new Streams(), new Drafts());
        service.acceptChunk(context, conversation, generation, new StreamChunk(message, 0, "done", true, "STOP"));
        SseStreamController controller = new SseStreamController(service, new SseEventHub());
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", true); request.addHeader("X-Tenant-Id", context.tenantId()); request.addHeader("X-Subject-Id", context.subjectId()); request.addHeader("X-Trace-Id", context.traceId()); request.addHeader("X-Request-Id", context.requestId());
        assertEquals(0L, controller.stream(conversation, 6, request).getTimeout());
    }
    private record Messages(UUID conversation, UUID message, TrustedConversationRequestContext context) implements ConversationRepository {
        public List<Message> listMessages(String tenant, UUID id, long after, int limit) { return tenant.equals(context.tenantId()) && id.equals(conversation) ? List.of(new Message(message, tenant, id, 7, "AI", "TEXT", "PUBLIC", "", "STREAMING", context.requestId(), "{}", Instant.now())) : List.of(); }
        public Optional<Message> findUserMessage(String a, String b, UUID c, UUID d) { return Optional.empty(); } public com.bn.aliagent.conversation.core.ConversationModels.Conversation create(com.bn.aliagent.conversation.core.ConversationModels.Conversation a) { return null; } public Optional<com.bn.aliagent.conversation.core.ConversationModels.Conversation> findConversation(UUID a, String b) { return Optional.empty(); } public List<com.bn.aliagent.conversation.core.ConversationModels.Conversation> listConversations(String a, int b, int c) { return List.of(); } public long countConversations(String a) { return 0; } public com.bn.aliagent.conversation.core.ConversationModels.Conversation update(com.bn.aliagent.conversation.core.ConversationModels.Conversation a) { return null; } public void softDelete(UUID a, String b) { } public Message appendUserMessage(Message a, String b) { return null; } public void enqueue(com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest a) { } public List<com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest> pendingReplies(int a) { return List.of(); } public void markPublished(UUID a) { }
    }
    private static final class Streams implements StreamingRepository { private final java.util.Map<UUID, com.bn.aliagent.conversation.streaming.StreamingModels.Generation> values = new java.util.HashMap<>(); public Optional<com.bn.aliagent.conversation.streaming.StreamingModels.Generation> find(String t, UUID c, UUID g) { return Optional.ofNullable(values.get(g)); } public Optional<com.bn.aliagent.conversation.streaming.StreamingModels.Generation> findByMessage(String t, UUID c, UUID m) { return values.values().stream().filter(v -> v.messageId().equals(m)).findFirst(); } public com.bn.aliagent.conversation.streaming.StreamingModels.Generation save(com.bn.aliagent.conversation.streaming.StreamingModels.Generation v) { values.put(v.generationId(), v); return v; } public List<com.bn.aliagent.conversation.streaming.StreamingModels.Generation> terminalAfter(String t, UUID c, long s) { return values.values().stream().filter(v -> v.sequence() > s && v.status() != com.bn.aliagent.conversation.streaming.StreamingModels.GenerationStatus.STREAMING).toList(); } public List<com.bn.aliagent.conversation.streaming.StreamingModels.Generation> active() { return List.of(); } }
    private static final class Drafts implements DraftStore { public void save(com.bn.aliagent.conversation.streaming.StreamingModels.Generation g) { } public void markCancelled(com.bn.aliagent.conversation.streaming.StreamingModels.Generation g) { } public Optional<String> load(com.bn.aliagent.conversation.streaming.StreamingModels.Generation g) { return Optional.empty(); } }
}
