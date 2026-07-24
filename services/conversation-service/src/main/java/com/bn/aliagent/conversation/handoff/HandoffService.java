package com.bn.aliagent.conversation.handoff;

import java.util.UUID;

public final class HandoffService {
    private final HandoffRepository repository;

    public HandoffService(HandoffRepository repository) { this.repository = repository; }

    public HandoffResult release(HandoffCommand command) { return complete(command, "AI_ACTIVE", "RELEASED", false); }
    public HandoffResult close(HandoffCommand command) { return complete(command, "CLOSED", "CLOSED", false); }
    public HandoffResult forceRelease(HandoffCommand command) { return complete(command, "AI_ACTIVE", "FORCE_RELEASED", true); }

    private HandoffResult complete(HandoffCommand command, String status, String takeoverStatus, boolean forced) {
        if (command == null || command.requestId() == null || command.conversationId() == null || command.tenantId() == null || command.tenantId().isBlank() || !repository.conversationExists(command.tenantId(), command.conversationId())) throw new HandoffException("会话不属于当前租户");
        HandoffResult replay = repository.findByRequest(command.requestId());
        if (replay != null) return replay;
        if (forced) {
            if (!command.supervisor() || command.reason() == null || command.reason().isBlank()) throw new HandoffException("主管强制释放必须提供原因");
            repository.auditSupervisorAction(command.tenantId(), command.conversationId(), command.staffId(), "FORCE_RELEASE", command.reason());
        } else if (!repository.currentStaff(command.tenantId(), command.conversationId(), command.staffId())) {
            throw new HandoffException("仅当前客服可结束或关闭会话");
        }
        try { return repository.complete(command, status, takeoverStatus); }
        catch (IllegalStateException exception) { throw new HandoffException("已关闭会话不可恢复"); }
    }
}
