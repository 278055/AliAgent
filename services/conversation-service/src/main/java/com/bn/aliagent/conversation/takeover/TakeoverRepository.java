package com.bn.aliagent.conversation.takeover;

import java.util.UUID;

public interface TakeoverRepository {
    boolean conversationExists(String tenantId, UUID conversationId);
    boolean acceptedOrClaimed(TakeoverCommand command);
    Takeover findByRequest(String tenantId, UUID requestId);
    Takeover createActiveIfAbsent(Takeover takeover);
}
