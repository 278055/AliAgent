package com.bn.aliagent.conversation.transfer;

import java.time.Instant;
import java.util.UUID;

public record Transfer(UUID id, String tenantId, UUID conversationId, String sourceStaffId, String targetStaffId, String targetSkillGroupId, UUID requestId, String status, String reason, Instant createdAt) {
    public static Transfer skillGroup(SkillGroupTransferCommand command) { return new Transfer(UUID.randomUUID(), command.tenantId(), command.conversationId(), command.sourceStaffId(), null, command.skillGroupId(), command.requestId(), "REQUEUED", command.reason(), Instant.now()); }
    public Transfer withStatus(String value) { return new Transfer(id, tenantId, conversationId, sourceStaffId, targetStaffId, targetSkillGroupId, requestId, value, reason, createdAt); }
}
