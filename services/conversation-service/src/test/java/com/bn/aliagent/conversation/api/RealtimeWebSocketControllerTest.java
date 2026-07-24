package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import com.bn.aliagent.conversation.core.ConversationRepository;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.realtime.InMemoryRouteStore;
import com.bn.aliagent.conversation.realtime.RealtimeCollaborationService;
import com.bn.aliagent.conversation.realtime.RealtimeConnection;
import com.bn.aliagent.conversation.realtime.RealtimePublisher;
import com.bn.aliagent.conversation.realtime.RealtimeSessionRegistry;
import com.bn.aliagent.conversation.realtime.RealtimeStateRepository;
import com.bn.aliagent.conversation.realtime.RealtimeStateService;
import com.bn.aliagent.conversation.realtime.RedisRealtimeSubscriber;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class RealtimeWebSocketControllerTest {
    @Test
    void closesHandshakeWithoutTrustedContext() throws Exception {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        fixture.controller.onOpen(fixture.session, fixture.endpoint(new HashMap<>()));
        verify(fixture.session).close(org.mockito.ArgumentMatchers.argThat(reason ->
                reason.getCloseCode().equals(CloseReason.CloseCodes.VIOLATED_POLICY)));
    }

    @Test
    void rejectsNonStaffHumanMessageWithAuthCode() throws Exception {
        Fixture fixture = new Fixture("tenant-a", "MEMBER");
        fixture.controller.handle(fixture.session, fixture.context, UUID.randomUUID(),
                "{\"eventType\":\"human.send\",\"conversationId\":\"" + fixture.conversationId
                        + "\",\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"content\":\"test\"}");
        verify(fixture.remote).sendText(contains("AUTH-403-001"));
    }

    @Test
    void rejectsCrossTenantTakeover() throws Exception {
        Fixture fixture = new Fixture("tenant-b", "STAFF");
        fixture.controller.handle(fixture.session, fixture.context, UUID.randomUUID(),
                "{\"eventType\":\"human.takeover\",\"conversationId\":\"" + fixture.conversationId + "\"}");
        verify(fixture.remote).sendText(contains("TENANT-403-001"));
    }

    @Test
    void duplicateHumanMessageReturnsSamePersistedSequence() {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        UUID clientMessageId = UUID.randomUUID();
        Message first = fixture.conversations.submitStaffMessage(fixture.context, fixture.conversationId, "test", clientMessageId);
        Message replay = fixture.conversations.submitStaffMessage(fixture.context, fixture.conversationId, "test", clientMessageId);
        assertEquals(first.id(), replay.id());
        assertEquals(first.sequence(), replay.sequence());
        assertEquals(1, fixture.repository.messages.size());
    }

    @Test
    void presenceResponseDoesNotExposeInternalConnectionId() throws Exception {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        fixture.controller.handle(fixture.session, fixture.context, UUID.randomUUID(),
                "{\"eventType\":\"agent.presence\",\"conversationId\":\"" + fixture.conversationId
                        + "\",\"status\":\"ONLINE\"}");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(fixture.remote).sendText(payload.capture());
        assertTrue(payload.getValue().contains("\"content\":\"ONLINE\""));
        assertFalse(payload.getValue().contains("connectionId"));
    }

    @Test
    void rejectsNonStaffPresenceUpdate() throws Exception {
        Fixture fixture = new Fixture("tenant-a", "MEMBER");
        fixture.controller.handle(fixture.session, fixture.context, UUID.randomUUID(),
                "{\"eventType\":\"agent.presence\",\"conversationId\":\"" + fixture.conversationId
                        + "\",\"status\":\"ONLINE\"}");

        verify(fixture.remote).sendText(contains("AUTH-403-001"));
    }

    @Test
    void releaseRegistersConnectionBeforeBroadcastingStatus() {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        fixture.controller.handle(fixture.session, fixture.context, fixture.connectionId,
                "{\"eventType\":\"human.release\",\"conversationId\":\"" + fixture.conversationId + "\"}");

        assertEquals(1, fixture.routes.find("tenant-a", fixture.conversationId).size());
    }

    @Test
    void closeStillCleansDatabaseStateWhenRedisIsUnavailable() {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        fixture.session.getUserProperties().put(RealtimeWebSocketController.CONTEXT, fixture.context);
        fixture.session.getUserProperties().put(RealtimeWebSocketController.CONNECTION, fixture.connectionId);
        RealtimeStateService state = new RealtimeStateService(fixture.stateRepository);
        RealtimeWebSocketController controller = new RealtimeWebSocketController(fixture.conversations,
                new RealtimeCollaborationService("instance-a", new FailingRouteStore(), (channel, envelope) -> { }),
                new RealtimeSessionRegistry(), state, fixture.subscriber);

        controller.onClose(fixture.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "closed"));

        assertEquals(1, fixture.stateRepository.deletedConnections);
        assertEquals("OFFLINE", fixture.stateRepository.lastPresence);
    }

    @Test
    void closingOneOfTwoStaffSessionsKeepsPresenceOnline() {
        Fixture fixture = new Fixture("tenant-a", "STAFF");
        UUID otherConnection = UUID.randomUUID();
        fixture.session.getUserProperties().put(RealtimeWebSocketController.CONTEXT, fixture.context);
        fixture.session.getUserProperties().put(RealtimeWebSocketController.CONNECTION, fixture.connectionId);
        fixture.sessions.add(fixture.connectionId, "tenant-a", "staff-1", fixture.session);
        fixture.sessions.add(otherConnection, "tenant-a", "staff-1", mock(Session.class));

        fixture.controller.onClose(fixture.session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "closed"));

        assertEquals(null, fixture.stateRepository.lastPresence);
    }

    private static final class Fixture {
        private final UUID conversationId = UUID.randomUUID();
        private final Repository repository = new Repository(conversationId);
        private final ConversationService conversations = new ConversationService(repository);
        private final TrustedConversationRequestContext context;
        private final Session session = mock(Session.class);
        private final RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        private final UUID connectionId = UUID.randomUUID();
        private final InMemoryRouteStore routes = new InMemoryRouteStore();
        private final RealtimeSessionRegistry sessions = new RealtimeSessionRegistry();
        private final RedisRealtimeSubscriber subscriber = new RedisRealtimeSubscriber("localhost", 1, "", "test", new RealtimeSessionRegistry());
        private final RealtimeWebSocketController controller;
        private Fixture(String tenant, String type) {
            context = new TrustedConversationRequestContext(tenant, "staff-1", type, "trace", UUID.randomUUID());
            when(session.getBasicRemote()).thenReturn(remote);
            when(session.getUserProperties()).thenReturn(new HashMap<>());
            RealtimeStateService state = new RealtimeStateService(stateRepository);
            controller = new RealtimeWebSocketController(conversations,
                    new RealtimeCollaborationService("instance-a", routes, (channel, envelope) -> { }),
                    sessions, state, subscriber);
        }
        private final StateRepository stateRepository = new StateRepository();
        private EndpointConfig endpoint(Map<String, Object> properties) {
            EndpointConfig config = mock(EndpointConfig.class);
            when(config.getUserProperties()).thenReturn(properties);
            return config;
        }
    }

    private static final class StateRepository implements RealtimeStateRepository {
        private int deletedConnections;
        private String lastPresence;
        public void saveConnection(RealtimeConnection value) { }
        public void heartbeat(RealtimeConnection value) { }
        public void deleteConnection(String tenantId, UUID connectionId) { deletedConnections++; }
        public void savePresence(String tenantId, String staffId, String status) { lastPresence = status; }
        public void saveHumanState(String tenantId, UUID conversationId, String staffId, String status) { }
    }

    private static final class FailingRouteStore implements com.bn.aliagent.conversation.realtime.RealtimeRouteStore {
        public void bind(RealtimeConnection connection) { throw new IllegalStateException("Redis unavailable"); }
        public void unbind(String tenantId, UUID connectionId) { throw new IllegalStateException("Redis unavailable"); }
        public List<RealtimeConnection> find(String tenantId, UUID conversationId) { throw new IllegalStateException("Redis unavailable"); }
    }

    private static final class Repository implements ConversationRepository {
        private final Conversation conversation;
        private final List<Message> messages = new ArrayList<>();
        private final Map<UUID, Message> staffMessages = new HashMap<>();
        private Repository(UUID id) { Instant now = Instant.now(); conversation = new Conversation(id, "tenant-a", "member-1", "test-p4-c", "AI_ACTIVE", false, now, now); }
        public Conversation create(Conversation value) { return value; }
        public Optional<Conversation> findConversation(UUID id, String tenant) { return id.equals(conversation.id()) && tenant.equals(conversation.tenantId()) ? Optional.of(conversation) : Optional.empty(); }
        public List<Conversation> listConversations(String tenant, int offset, int limit) { return List.of(); }
        public long countConversations(String tenant) { return 0; }
        public Conversation update(Conversation value) { return value; }
        public void softDelete(UUID id, String tenant) { }
        public Optional<Message> findUserMessage(String tenant, String subject, UUID conversation, UUID request) { return Optional.empty(); }
        public Optional<Message> findStaffMessage(String tenant, String subject, UUID conversation, UUID clientMessageId) { return Optional.ofNullable(staffMessages.get(clientMessageId)); }
        public Message appendUserMessage(Message value, String subject) { return value; }
        public Message appendStaffMessage(Message value, String subject, UUID clientMessageId) { Message saved = new Message(value.id(), value.tenantId(), value.conversationId(), messages.size() + 1, value.senderType(), value.messageType(), value.visibility(), value.content(), value.status(), value.requestId(), value.metadata(), value.createdAt()); messages.add(saved); staffMessages.put(clientMessageId, saved); return saved; }
        public Message appendAiStreamingMessage(Message value, UUID generationId) { return value; }
        public Optional<Message> findAiGeneration(String tenant, UUID conversation, UUID request) { return Optional.empty(); }
        public List<Message> listMessages(String tenant, UUID conversation, long after, int limit) { return List.of(); }
        public void enqueue(ReplyRequest value) { }
        public List<ReplyRequest> pendingReplies(int limit) { return List.of(); }
        public void markPublished(UUID eventId) { }
    }
}
