package com.bn.aliagent.conversation.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InMemoryRouteStore implements RealtimeRouteStore {
    private final List<RealtimeConnection> connections = new ArrayList<>();
    @Override public synchronized void bind(RealtimeConnection connection) { connections.removeIf(value -> value.connectionId().equals(connection.connectionId())); connections.add(connection); }
    @Override public synchronized void unbind(String tenantId, UUID connectionId) { connections.removeIf(value -> value.tenantId().equals(tenantId) && value.connectionId().equals(connectionId)); }
    @Override public synchronized List<RealtimeConnection> find(String tenantId, UUID conversationId) { return connections.stream().filter(value -> value.tenantId().equals(tenantId) && value.conversationId().equals(conversationId)).toList(); }
}
