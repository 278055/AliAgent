package com.bn.aliagent.conversation.staffmessage;

import java.util.UUID;

public record StaffMessageCommand(String tenantId, UUID conversationId, String staffId, String content, UUID clientMessageId, UUID requestId) { }
