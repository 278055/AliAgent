package com.bn.aliagent.conversation.realtime;

import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealtimeSessionRegistry {
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    private final Map<UUID, LocalSession> sessions = new ConcurrentHashMap<>();
    public void add(UUID connectionId, String tenantId, Session session) { add(connectionId, tenantId, "", session); }
    public void add(UUID connectionId, String tenantId, String subjectId, Session session) { sessions.put(connectionId, new LocalSession(tenantId, subjectId, session)); }
    public void remove(UUID connectionId) { sessions.remove(connectionId); }
    public boolean hasSession(String tenantId) { return sessions.values().stream().anyMatch(value -> value.tenantId().equals(tenantId)); }
    public boolean hasSubjectSession(String tenantId, String subjectId) { return sessions.values().stream().anyMatch(value -> value.tenantId().equals(tenantId) && value.subjectId().equals(subjectId)); }
    public void send(RealtimeEnvelope envelope) {
        LocalSession local = sessions.get(envelope.connectionId());
        if (local == null || !local.tenantId().equals(envelope.tenantId()) || !local.session().isOpen()) return;
        try { local.session().getBasicRemote().sendText(envelope.publicJson()); }
        catch (IOException exception) { throw new IllegalStateException("WebSocket delivery failed", exception); }
    }
    private record LocalSession(String tenantId, String subjectId, Session session) { }
}
