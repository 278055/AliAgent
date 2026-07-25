package com.bn.aliagent.conversation.queue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface VerifiedConversationTagRepository {
    Set<String> verifiedTags(String tenantId, UUID conversationId);
    void verify(String tenantId, UUID conversationId, String tagCode, String source, Instant verifiedAt);
}
