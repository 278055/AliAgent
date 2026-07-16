package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationModels;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.realtime.RealtimeCollaborationService;
import com.bn.aliagent.conversation.realtime.RealtimeConnection;
import com.bn.aliagent.conversation.realtime.RealtimeEnvelope;
import com.bn.aliagent.conversation.realtime.RealtimeSessionRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import java.time.Instant;
import java.util.UUID;

/** WebSocket 只接收经过网关验证后注入的可信身份头，不接受消息体中的租户或客服身份。 */
public final class RealtimeWebSocketController extends Endpoint {
    public static final String CONTEXT = "realtime.context";
    public static final String CONNECTION = "realtime.connection";
    private final ConversationService conversations;
    private final RealtimeCollaborationService realtime;
    private final RealtimeSessionRegistry sessions;
    private final ObjectMapper json = new ObjectMapper();

    public RealtimeWebSocketController(ConversationService conversations, RealtimeCollaborationService realtime, RealtimeSessionRegistry sessions) {
        this.conversations = conversations;
        this.realtime = realtime;
        this.sessions = sessions;
    }

    @Override public void onOpen(Session session, EndpointConfig config) {
        TrustedConversationRequestContext context = (TrustedConversationRequestContext) config.getUserProperties().get(CONTEXT);
        if (context == null) { try { session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "AUTH-401-001")); } catch (Exception ignored) { } return; }
        UUID connectionId = UUID.randomUUID();
        session.getUserProperties().put(CONTEXT, context);
        session.getUserProperties().put(CONNECTION, connectionId);
        sessions.add(connectionId, context.tenantId(), session);
        session.addMessageHandler(String.class, payload -> receive(session, context, connectionId, payload));
    }

    @Override public void onClose(Session session, CloseReason reason) {
        Object connection = session.getUserProperties().get(CONNECTION);
        Object context = session.getUserProperties().get(CONTEXT);
        if (connection instanceof UUID connectionId && context instanceof TrustedConversationRequestContext trusted) {
            sessions.remove(connectionId);
            realtime.unregister(trusted.tenantId(), connectionId);
        }
    }

    private void receive(Session session, TrustedConversationRequestContext context, UUID connectionId, String payload) {
        try {
            JsonNode event = json.readTree(payload);
            String type = event.path("eventType").asText();
            UUID conversationId = UUID.fromString(event.path("conversationId").asText());
            if ("human.send".equals(type)) {
                ConversationModels.Message message = conversations.submitStaffMessage(context, conversationId, event.path("content").asText(), UUID.fromString(event.path("clientMessageId").asText()));
                realtime.register(new RealtimeConnection(context.tenantId(), conversationId, connectionId, realtime.instanceId()));
                if (!realtime.deliver(messageEnvelope(context, message)).delivered()) sendError(session, context, conversationId, "SYSTEM-503-REDIS", "Message persisted; reconnect and replay by sequence");
            } else if ("human.takeover".equals(type)) {
                var conversation = conversations.takeOver(context, conversationId);
                realtime.register(new RealtimeConnection(context.tenantId(), conversationId, connectionId, realtime.instanceId()));
                if (!realtime.deliver(statusEnvelope(context, conversation)).delivered()) sendError(session, context, conversationId, "SYSTEM-503-REDIS", "Status persisted; reconnect and replay by sequence");
            } else if ("human.release".equals(type)) {
                var conversation = conversations.release(context, conversationId);
                if (!realtime.deliver(statusEnvelope(context, conversation)).delivered()) sendError(session, context, conversationId, "SYSTEM-503-REDIS", "Status persisted; reconnect and replay by sequence");
            } else { sendError(session, context, conversationId, "CONV-400-001", "Unsupported event type"); }
        } catch (Exception exception) { sendError(session, context, null, "CONV-400-001", "Invalid realtime event"); }
    }

    private RealtimeEnvelope messageEnvelope(TrustedConversationRequestContext context, ConversationModels.Message value) {
        return new RealtimeEnvelope("human.message", context.tenantId(), value.conversationId(), context.requestId(), Instant.now(), value.id(), value.sequence(), "STAFF", value.content(), null);
    }
    private RealtimeEnvelope statusEnvelope(TrustedConversationRequestContext context, ConversationModels.Conversation value) {
        return new RealtimeEnvelope("conversation.status", context.tenantId(), value.id(), context.requestId(), Instant.now(), null, 0, null, value.status(), null);
    }
    private void sendError(Session session, TrustedConversationRequestContext context, UUID conversationId, String code, String message) {
        try { session.getBasicRemote().sendText(json.writeValueAsString(new RealtimeEnvelope("error", context.tenantId(), conversationId, context.requestId(), Instant.now(), null, 0, null, message, null))); }
        catch (Exception ignored) { }
    }
}
