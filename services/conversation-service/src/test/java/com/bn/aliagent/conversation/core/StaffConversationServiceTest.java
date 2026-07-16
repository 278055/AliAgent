package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffConversationServiceTest {
    @Test
    void requiresStaffSubjectForHumanMessages() {
        assertThrows(ConversationException.class, () -> ConversationPolicy.requireStaff("MEMBER"));
        assertEquals("STAFF", ConversationPolicy.requireStaff("STAFF"));
    }

    @Test
    void requiresClientMessageIdForHumanMessageDeduplication() {
        assertThrows(ConversationException.class, () -> ConversationPolicy.requireClientMessageId(null));
        UUID value = UUID.randomUUID();
        assertEquals(value, ConversationPolicy.requireClientMessageId(value));
    }
}
