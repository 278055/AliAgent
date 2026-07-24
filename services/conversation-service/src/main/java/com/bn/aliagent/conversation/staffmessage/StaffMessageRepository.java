package com.bn.aliagent.conversation.staffmessage;

import java.util.UUID;

public interface StaffMessageRepository {
    ConversationAccess conversation(String tenantId, UUID conversationId);
    StaffMessage findByClientMessageId(String tenantId, String staffId, UUID clientMessageId);
    StaffMessage append(StaffMessage message);
}
