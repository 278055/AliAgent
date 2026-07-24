package com.bn.aliagent.conversation.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferServiceTest {
    @Test
    void 指定客服接受前原客服仍保持active责任() {
        Repository repository = new Repository();
        TransferService service = new TransferService(repository);
        Transfer transfer = service.requestAgentTransfer(new AgentTransferCommand("tenant-a", UUID.randomUUID(), "staff-1",
                "staff-2", UUID.randomUUID(), false, null));
        assertEquals("staff-1", repository.currentAgent);
        service.accept(transfer.id(), "tenant-a", "staff-2", UUID.randomUUID());
        assertEquals("staff-2", repository.currentAgent);
    }

    @Test
    void 技能组转派失败时原责任保留且离线容量满和跨租户被拒绝() {
        Repository repository = new Repository();
        TransferService service = new TransferService(repository);
        repository.failSkillGroupTransfer = true;
        assertThrows(TransferException.class, () -> service.transferToSkillGroup(new SkillGroupTransferCommand("tenant-a",
                UUID.randomUUID(), "staff-1", "after-sale", UUID.randomUUID(), false, null)));
        assertEquals("staff-1", repository.currentAgent);
        repository.online = false;
        assertThrows(TransferException.class, () -> service.requestAgentTransfer(new AgentTransferCommand("tenant-a",
                UUID.randomUUID(), "staff-1", "staff-2", UUID.randomUUID(), false, null)));
        repository.online = true;
        repository.capacity = false;
        assertThrows(TransferException.class, () -> service.requestAgentTransfer(new AgentTransferCommand("tenant-a",
                UUID.randomUUID(), "staff-1", "staff-2", UUID.randomUUID(), false, null)));
        assertThrows(TransferException.class, () -> service.requestAgentTransfer(new AgentTransferCommand("tenant-b",
                UUID.randomUUID(), "staff-1", "staff-2", UUID.randomUUID(), false, null)));
    }

    @Test
    void 主管强制转派必须带原因且目标接受幂等() {
        Repository repository = new Repository();
        TransferService service = new TransferService(repository);
        assertThrows(TransferException.class, () -> service.forceAgentTransfer(new AgentTransferCommand("tenant-a",
                UUID.randomUUID(), "supervisor", "staff-2", UUID.randomUUID(), true, " ")));
        Transfer transfer = service.forceAgentTransfer(new AgentTransferCommand("tenant-a", UUID.randomUUID(),
                "supervisor", "staff-2", UUID.randomUUID(), true, "异常断线"));
        assertEquals(service.accept(transfer.id(), "tenant-a", "staff-2", UUID.randomUUID()).id(),
                service.accept(transfer.id(), "tenant-a", "staff-2", UUID.randomUUID()).id());
        assertEquals("FORCE_TRANSFER:异常断线", repository.audit);
    }

    private static final class Repository implements TransferRepository {
        private String currentAgent = "staff-1";
        private boolean online = true;
        private boolean capacity = true;
        private boolean failSkillGroupTransfer;
        private String audit;
        private final Map<UUID, Transfer> transfers = new HashMap<>();
        public String currentAgent(String tenantId, UUID conversationId) { return "tenant-a".equals(tenantId) ? currentAgent : null; }
        public boolean targetEligible(String tenantId, String staffId) { return "tenant-a".equals(tenantId) && online && capacity && "staff-2".equals(staffId); }
        public Transfer findByRequest(UUID requestId) { return transfers.values().stream().filter(value -> value.requestId().equals(requestId)).findFirst().orElse(null); }
        public Transfer save(Transfer transfer) { transfers.put(transfer.id(), transfer); return transfer; }
        public Transfer find(UUID id, String tenantId) { return "tenant-a".equals(tenantId) ? transfers.get(id) : null; }
        public Transfer acceptAndReplace(Transfer transfer) { currentAgent = transfer.targetStaffId(); Transfer accepted = transfer.withStatus("ACCEPTED"); transfers.put(transfer.id(), accepted); return accepted; }
        public Transfer requeueAndTransfer(SkillGroupTransferCommand command) { if (failSkillGroupTransfer) throw new IllegalStateException("queue unavailable"); currentAgent = null; return save(Transfer.skillGroup(command)); }
        public void auditSupervisorAction(String tenantId, UUID conversationId, String supervisorId, String action, String reason) { audit = action + ":" + reason; }
    }
}
