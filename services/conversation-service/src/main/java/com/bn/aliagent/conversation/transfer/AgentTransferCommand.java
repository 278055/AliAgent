package com.bn.aliagent.conversation.transfer;

import java.util.UUID;

public record AgentTransferCommand(String tenantId, UUID conversationId, String sourceStaffId, String targetStaffId, UUID requestId, boolean supervisor, String reason) { }
