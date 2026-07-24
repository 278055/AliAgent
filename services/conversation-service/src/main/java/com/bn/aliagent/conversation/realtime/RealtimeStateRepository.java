package com.bn.aliagent.conversation.realtime;

import java.util.UUID;

public interface RealtimeStateRepository {
    void saveConnection(RealtimeConnection connection);
    void heartbeat(RealtimeConnection connection);
    void deleteConnection(String tenantId, UUID connectionId);
    void savePresence(String tenantId, String staffId, String status);
    void saveHumanState(String tenantId, UUID conversationId, String staffId, String status);
}
