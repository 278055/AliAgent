package com.bn.aliagent.conversation.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HandoffServiceTest {
    @Test
    void 普通结束恢复ai而显式关闭进入closed() {
        Repository repository = new Repository();
        HandoffService service = new HandoffService(repository);
        assertEquals("AI_ACTIVE", service.release(new HandoffCommand("tenant-a", UUID.randomUUID(), "staff-1", UUID.randomUUID(), false, null)).conversationStatus());
        repository.status = "HUMAN_ACTIVE";
        assertEquals("CLOSED", service.close(new HandoffCommand("tenant-a", UUID.randomUUID(), "staff-1", UUID.randomUUID(), false, null)).conversationStatus());
    }

    @Test
    void 关闭不可恢复且主管强制释放需要理由并拒绝跨租户() {
        Repository repository = new Repository();
        HandoffService service = new HandoffService(repository);
        repository.status = "CLOSED";
        assertThrows(HandoffException.class, () -> service.release(new HandoffCommand("tenant-a", UUID.randomUUID(), "staff-1", UUID.randomUUID(), false, null)));
        assertThrows(HandoffException.class, () -> service.forceRelease(new HandoffCommand("tenant-a", UUID.randomUUID(), "supervisor", UUID.randomUUID(), true, " ")));
        assertThrows(HandoffException.class, () -> service.forceRelease(new HandoffCommand("tenant-b", UUID.randomUUID(), "supervisor", UUID.randomUUID(), true, "离线超时")));
        repository.status = "HUMAN_ACTIVE";
        assertEquals("FORCE_RELEASED", service.forceRelease(new HandoffCommand("tenant-a", UUID.randomUUID(), "supervisor", UUID.randomUUID(), true, "离线超时")).takeoverStatus());
        assertEquals("FORCE_RELEASE:离线超时", repository.audit);
    }

    private static final class Repository implements HandoffRepository {
        private String status = "HUMAN_ACTIVE";
        private final Map<UUID, HandoffResult> results = new HashMap<>();
        private String audit;
        public HandoffResult findByRequest(UUID requestId) { return results.get(requestId); }
        public boolean currentStaff(String tenantId, UUID conversationId, String staffId) { return "tenant-a".equals(tenantId) && "staff-1".equals(staffId); }
        public boolean conversationExists(String tenantId, UUID conversationId) { return "tenant-a".equals(tenantId); }
        public HandoffResult complete(HandoffCommand command, String nextStatus, String takeoverStatus) { if ("CLOSED".equals(status)) throw new IllegalStateException("closed"); status = nextStatus; HandoffResult result = new HandoffResult(command.requestId(), nextStatus, takeoverStatus, command.reason()); results.put(command.requestId(), result); return result; }
        public void auditSupervisorAction(String tenantId, UUID conversationId, String supervisorId, String action, String reason) { audit = action + ":" + reason; }
    }
}
