package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeEnvelopeTest {
    @Test
    void errorEnvelopeKeepsContractCodeAndMessage() {
        UUID requestId = UUID.randomUUID();
        RealtimeEnvelope envelope = RealtimeEnvelope.error("test-p4-c-tenant", UUID.randomUUID(), requestId,
                "TENANT-403-001", "Conversation is not accessible");

        assertEquals("error", envelope.eventType());
        assertEquals("TENANT-403-001", envelope.code());
        assertEquals("Conversation is not accessible", envelope.message());
        assertEquals(requestId, envelope.requestId());
    }

    @Test
    void publicPayloadOmitsInternalConnectionAndUsesStatusField() throws Exception {
        RealtimeEnvelope envelope = RealtimeEnvelope.status("test-p4-c-tenant", UUID.randomUUID(), UUID.randomUUID(),
                "HUMAN_ACTIVE").withConnectionId(UUID.randomUUID());
        String payload = envelope.publicJson();
        assertFalse(payload.contains("connectionId"));
        assertEquals("HUMAN_ACTIVE", new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).path("status").asText());
    }
}
