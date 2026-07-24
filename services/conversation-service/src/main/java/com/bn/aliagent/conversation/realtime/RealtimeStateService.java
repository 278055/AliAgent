package com.bn.aliagent.conversation.realtime;

import java.util.Set;
import java.util.UUID;

public final class RealtimeStateService {
    private static final Set<String> PRESENCE = Set.of("ONLINE", "OFFLINE", "BUSY");
    private static final Set<String> HUMAN_STATE = Set.of("WAITING_HUMAN", "HUMAN_ACTIVE", "AI_ACTIVE");
    private final RealtimeStateRepository repository;

    public RealtimeStateService(RealtimeStateRepository repository) { this.repository = repository; }
    public void connected(RealtimeConnection connection) { repository.saveConnection(connection); }
    public void heartbeat(RealtimeConnection connection) { repository.heartbeat(connection); }
    public void disconnected(String tenantId, UUID connectionId) { repository.deleteConnection(tenantId, connectionId); }
    public void updatePresence(String tenantId, String staffId, String status) {
        if (!PRESENCE.contains(status)) throw new IllegalArgumentException("Unsupported presence status");
        repository.savePresence(tenantId, staffId, status);
    }
    public void updateHumanState(String tenantId, UUID conversationId, String staffId, String status) {
        if (!HUMAN_STATE.contains(status)) throw new IllegalArgumentException("Unsupported human state");
        repository.saveHumanState(tenantId, conversationId, staffId, status);
    }
}
