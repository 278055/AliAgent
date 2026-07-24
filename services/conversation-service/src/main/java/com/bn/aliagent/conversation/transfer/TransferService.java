package com.bn.aliagent.conversation.transfer;

import java.time.Instant;
import java.util.UUID;

public final class TransferService {
    private final TransferRepository repository;

    public TransferService(TransferRepository repository) { this.repository = repository; }

    public Transfer requestAgentTransfer(AgentTransferCommand command) { return requestAgentTransfer(command, false); }

    public Transfer forceAgentTransfer(AgentTransferCommand command) { return requestAgentTransfer(command, true); }

    private Transfer requestAgentTransfer(AgentTransferCommand command, boolean forced) {
        validate(command.tenantId(), command.conversationId(), command.sourceStaffId(), command.requestId());
        Transfer replay = repository.findByRequest(command.requestId());
        if (replay != null) return replay;
        if (forced) { requireSupervisorReason(command); repository.auditSupervisorAction(command.tenantId(), command.conversationId(), command.sourceStaffId(), "FORCE_TRANSFER", command.reason()); }
        else requireCurrent(command.tenantId(), command.conversationId(), command.sourceStaffId());
        if (!repository.targetEligible(command.tenantId(), command.targetStaffId())) throw new TransferException("目标客服不满足同租户、技能、在线或容量要求");
        return repository.save(new Transfer(UUID.randomUUID(), command.tenantId(), command.conversationId(), command.sourceStaffId(),
                command.targetStaffId(), null, command.requestId(), "PENDING", command.reason(), Instant.now()));
    }

    public Transfer accept(UUID transferId, String tenantId, String targetStaffId, UUID requestId) {
        Transfer transfer = repository.find(transferId, tenantId);
        if (transfer == null || !targetStaffId.equals(transfer.targetStaffId())) throw new TransferException("转派不属于当前客服或租户");
        if ("ACCEPTED".equals(transfer.status())) return transfer;
        if (!"PENDING".equals(transfer.status()) || !repository.targetEligible(tenantId, targetStaffId)) throw new TransferException("转派目标不可接受");
        return repository.acceptAndReplace(transfer);
    }

    public Transfer transferToSkillGroup(SkillGroupTransferCommand command) {
        validate(command.tenantId(), command.conversationId(), command.sourceStaffId(), command.requestId());
        Transfer replay = repository.findByRequest(command.requestId());
        if (replay != null) return replay;
        if (command.supervisor()) { requireSupervisorReason(command); repository.auditSupervisorAction(command.tenantId(), command.conversationId(), command.sourceStaffId(), "FORCE_TRANSFER", command.reason()); }
        else requireCurrent(command.tenantId(), command.conversationId(), command.sourceStaffId());
        try { return repository.requeueAndTransfer(command); }
        catch (RuntimeException exception) { throw new TransferException("转技能组失败，原客服责任保持不变"); }
    }

    private void requireCurrent(String tenantId, UUID conversationId, String staffId) {
        if (!staffId.equals(repository.currentAgent(tenantId, conversationId))) throw new TransferException("仅当前客服可转派");
    }
    private void validate(String tenantId, UUID conversationId, String staffId, UUID requestId) {
        if (tenantId == null || tenantId.isBlank() || conversationId == null || staffId == null || staffId.isBlank() || requestId == null) throw new TransferException("转派命令不合法");
    }
    private void requireSupervisorReason(AgentTransferCommand command) { if (!command.supervisor() || command.reason() == null || command.reason().isBlank()) throw new TransferException("主管强制转派必须提供原因"); }
    private void requireSupervisorReason(SkillGroupTransferCommand command) { if (!command.supervisor() || command.reason() == null || command.reason().isBlank()) throw new TransferException("主管强制转派必须提供原因"); }
}
