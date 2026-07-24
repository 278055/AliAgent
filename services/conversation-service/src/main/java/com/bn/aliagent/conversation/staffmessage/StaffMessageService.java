package com.bn.aliagent.conversation.staffmessage;

import java.time.Instant;
import java.util.UUID;

public final class StaffMessageService {
    private final StaffMessageRepository repository;

    public StaffMessageService(StaffMessageRepository repository) { this.repository = repository; }

    public StaffMessage send(StaffMessageCommand command) {
        validate(command);
        ConversationAccess access = repository.conversation(command.tenantId(), command.conversationId());
        if (access == null) throw new StaffMessageException("会话不属于当前租户");
        if (!"HUMAN_ACTIVE".equals(access.status())) throw new StaffMessageException("当前会话不能发送人工公开消息");
        if (!command.staffId().equals(access.currentStaffId())) throw new StaffMessageException("仅当前客服可发送人工公开消息");
        StaffMessage replay = repository.findByRequestId(command.tenantId(), command.requestId());
        if (replay == null) replay = repository.findByClientMessageId(command.tenantId(), command.conversationId(), command.staffId(), command.clientMessageId());
        if (replay != null) {
            if (!replay.conversationId().equals(command.conversationId()) || !replay.staffId().equals(command.staffId())) throw new StaffMessageException("幂等键与消息命令不匹配");
            return replay;
        }
        return repository.appendIfAbsent(new StaffMessage(UUID.randomUUID(), command.tenantId(), command.conversationId(), command.staffId(),
                "STAFF", "PUBLIC", command.content(), command.clientMessageId(), command.requestId(), Instant.now()));
    }

    private void validate(StaffMessageCommand command) {
        if (command == null || blank(command.tenantId()) || command.conversationId() == null || blank(command.staffId()) || blank(command.content()) || command.clientMessageId() == null || command.requestId() == null) throw new StaffMessageException("人工消息命令不完整");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
