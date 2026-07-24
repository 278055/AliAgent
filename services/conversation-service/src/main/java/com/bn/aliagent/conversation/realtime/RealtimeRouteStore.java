package com.bn.aliagent.conversation.realtime;

import java.util.List;
import java.util.UUID;

public interface RealtimeRouteStore {
    void bind(RealtimeConnection connection);
    void unbind(String tenantId, UUID connectionId);
    List<RealtimeConnection> find(String tenantId, UUID conversationId);
}
