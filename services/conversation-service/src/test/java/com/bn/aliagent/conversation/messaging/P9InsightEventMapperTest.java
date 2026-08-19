package com.bn.aliagent.conversation.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class P9InsightEventMapperTest {
    @Test
    void firstPublicReplyProducesOnlyAnonymousEvidenceSignal() {
        HumanCollaborationEvent source = new HumanCollaborationEvent(UUID.randomUUID(),
                "conversation.human.first-public-reply", "test-tenant", UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-01T00:00:00Z"), "staff-1", "不应离开会话服务的原始内容", "HUMAN_ACTIVE",
                null, null, null, null, null);

        var envelope = P9InsightEventMapper.map(source);

        assertEquals("conversation.human.first-public-reply", envelope.get("eventType"));
        assertEquals("first-human-public-reply", ((java.util.Map<?, ?>) envelope.get("payload")).get("signal"));
        assertFalse(envelope.toString().contains("原始内容"));
    }
}
