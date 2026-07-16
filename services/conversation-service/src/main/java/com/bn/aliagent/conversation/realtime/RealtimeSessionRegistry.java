package com.bn.aliagent.conversation.realtime;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealtimeSessionRegistry {
    private final Map<UUID, LocalSession> sessions = new ConcurrentHashMap<>();
    public void add(UUID connectionId, String tenantId, Session session) { sessions.put(connectionId, new LocalSession(tenantId, session)); }
    public void remove(UUID connectionId) { sessions.remove(connectionId); }
    public void send(RealtimeEnvelope envelope) {
        LocalSession local = sessions.get(envelope.connectionId());
        if (local == null || !local.tenantId().equals(envelope.tenantId()) || !local.session().isOpen()) return;
        try { local.session().getBasicRemote().sendText(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(envelope)); }
        catch (IOException exception) { throw new IllegalStateException("WebSocket delivery failed", exception); }
    }
    private record LocalSession(String tenantId, Session session) { }
}
