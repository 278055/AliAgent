package com.bn.aliagent.conversation.transfer;

import java.util.UUID;

public record SkillGroupTransferCommand(String tenantId, UUID conversationId, String sourceStaffId, String skillGroupId, UUID requestId, boolean supervisor, String reason) { }
