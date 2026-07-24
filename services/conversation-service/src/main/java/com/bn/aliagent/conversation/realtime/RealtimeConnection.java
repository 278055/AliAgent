package com.bn.aliagent.conversation.realtime;

import java.util.UUID;

public record RealtimeConnection(String tenantId, UUID conversationId, UUID connectionId, String instanceId) { }
