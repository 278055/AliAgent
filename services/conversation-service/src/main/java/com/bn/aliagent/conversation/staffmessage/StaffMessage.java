package com.bn.aliagent.conversation.staffmessage;

import java.time.Instant;
import java.util.UUID;

public record StaffMessage(UUID id, String tenantId, UUID conversationId, String staffId, String senderType, String visibility, String content, UUID clientMessageId, UUID requestId, Instant createdAt) { }
