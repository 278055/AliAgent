package com.bn.aliagent.orchestration.messaging;

import com.bn.aliagent.orchestration.core.ReplyRequestedV2;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiReplyRequestedV2ConsumerTest {
    @Test
    void 应只接受完整v2请求() {
        AiReplyRequestedV2Mapper mapper = new AiReplyRequestedV2Mapper();
        UUID id = UUID.randomUUID();
        Map<String, Object> event = Map.of("eventId", id.toString(), "eventVersion", 2, "tenantId", "test-p5-a",
                "traceId", "trace", "occurredAt", Instant.now().toString(), "payload", Map.of(
                        "conversationId", UUID.randomUUID().toString(), "messageId", UUID.randomUUID().toString(),
                        "replyMessageId", UUID.randomUUID().toString(), "generationId", UUID.randomUUID().toString(), "requestId", UUID.randomUUID().toString()));

        ReplyRequestedV2 result = mapper.map(event);

        assertEquals(id, result.eventId());
    }

    @Test
    void 缺少关联标识时不得猜测或生成() {
        AiReplyRequestedV2Mapper mapper = new AiReplyRequestedV2Mapper();
        Map<String, Object> event = Map.of("eventId", UUID.randomUUID().toString(), "eventVersion", 2, "tenantId", "test-p5-a",
                "traceId", "trace", "occurredAt", Instant.now().toString(), "payload", Map.of(
                        "conversationId", UUID.randomUUID().toString(), "messageId", UUID.randomUUID().toString(), "requestId", UUID.randomUUID().toString()));

        assertThrows(IllegalArgumentException.class, () -> mapper.map(event));
    }
}
