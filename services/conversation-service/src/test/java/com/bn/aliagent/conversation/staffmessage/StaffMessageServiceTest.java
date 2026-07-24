package com.bn.aliagent.conversation.staffmessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffMessageServiceTest {
    @Test
    void 只有当前客服可以发送且clientMessageId幂等() {
        UUID conversation = UUID.randomUUID();
        Repository repository = new Repository(conversation);
        StaffMessageService service = new StaffMessageService(repository);
        UUID clientMessageId = UUID.randomUUID();
        StaffMessageCommand owner = new StaffMessageCommand("tenant-a", conversation, "staff-1", "您好", clientMessageId, UUID.randomUUID());

        assertThrows(StaffMessageException.class, () -> service.send(new StaffMessageCommand("tenant-a", conversation,
                "staff-2", "您好", UUID.randomUUID(), UUID.randomUUID())));
        StaffMessage first = service.send(owner);
        StaffMessage second = service.send(owner);
        assertEquals(first.id(), second.id());
        assertEquals(first.id(), service.send(new StaffMessageCommand("tenant-a", conversation, "staff-1", "您好", UUID.randomUUID(), owner.requestId())).id());
    }

    @Test
    void 空内容关闭会话和跨租户被拒绝() {
        UUID conversation = UUID.randomUUID();
        Repository repository = new Repository(conversation);
        StaffMessageService service = new StaffMessageService(repository);
        assertThrows(StaffMessageException.class, () -> service.send(new StaffMessageCommand("tenant-a", conversation,
                "staff-1", " ", UUID.randomUUID(), UUID.randomUUID())));
        repository.status = "CLOSED";
        assertThrows(StaffMessageException.class, () -> service.send(new StaffMessageCommand("tenant-a", conversation,
                "staff-1", "x", UUID.randomUUID(), UUID.randomUUID())));
        assertThrows(StaffMessageException.class, () -> service.send(new StaffMessageCommand("tenant-b", conversation,
                "staff-1", "x", UUID.randomUUID(), UUID.randomUUID())));
    }

    private static final class Repository implements StaffMessageRepository {
        private final UUID conversationId;
        private String status = "HUMAN_ACTIVE";
        private final Map<UUID, StaffMessage> messages = new HashMap<>();
        private Repository(UUID conversationId) { this.conversationId = conversationId; }
        public ConversationAccess conversation(String tenantId, UUID id) { return "tenant-a".equals(tenantId) && conversationId.equals(id) ? new ConversationAccess(status, "staff-1") : null; }
        public StaffMessage findByRequestId(String tenantId, UUID requestId) { return messages.values().stream().filter(message -> message.requestId().equals(requestId)).findFirst().orElse(null); }
        public StaffMessage findByClientMessageId(String tenantId, UUID conversationId, String staffId, UUID id) { StaffMessage message = messages.get(id); return message != null && message.conversationId().equals(conversationId) && message.staffId().equals(staffId) ? message : null; }
        public StaffMessage appendIfAbsent(StaffMessage message) { messages.putIfAbsent(message.clientMessageId(), message); return messages.get(message.clientMessageId()); }
    }
}
