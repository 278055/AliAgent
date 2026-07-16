package com.bn.aliagent.conversation.core;

import java.util.UUID;

public final class ConversationPolicy {
    private ConversationPolicy() { }

    public static void requireIdempotencyKey(UUID requestId, String key) {
        if (key == null || !key.equals(requestId.toString())) {
            throw new ConversationException("CONV-400-004", "Idempotency-Key must equal requestId");
        }
    }

    public static int requirePageSize(Integer size) {
        int value = size == null ? 20 : size;
        if (value < 1 || value > 100) {
            throw new ConversationException("CONV-400-003", "pageSize must be between 1 and 100");
        }
        return value;
    }
}
