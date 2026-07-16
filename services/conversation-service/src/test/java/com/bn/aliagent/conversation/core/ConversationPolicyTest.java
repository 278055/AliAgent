package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationPolicyTest {
    @Test
    void 相同请求幂等键必须等于请求标识() {
        UUID requestId = UUID.randomUUID();
        assertDoesNotThrow(() -> ConversationPolicy.requireIdempotencyKey(requestId, requestId.toString()));
        assertThrows(ConversationException.class,
                () -> ConversationPolicy.requireIdempotencyKey(requestId, UUID.randomUUID().toString()));
    }

    @Test
    void 单页消息数量不得超过一百() {
        assertDoesNotThrow(() -> ConversationPolicy.requirePageSize(100));
        assertThrows(ConversationException.class, () -> ConversationPolicy.requirePageSize(101));
    }
}
