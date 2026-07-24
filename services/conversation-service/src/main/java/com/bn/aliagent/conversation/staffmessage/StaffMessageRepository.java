package com.bn.aliagent.conversation.staffmessage;

import java.util.UUID;

public interface StaffMessageRepository {
    ConversationAccess conversation(String tenantId, UUID conversationId);
    StaffMessage findByRequestId(String tenantId, UUID requestId);
    StaffMessage findByClientMessageId(String tenantId, UUID conversationId, String staffId, UUID clientMessageId);
    StaffMessage appendIfAbsent(StaffMessage message);
}
