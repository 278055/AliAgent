package com.bn.aliagent.conversation.takeover;

import java.time.Instant;
import java.util.UUID;

public final class TakeoverService {
    private final TakeoverRepository repository;

    public TakeoverService(TakeoverRepository repository) { this.repository = repository; }

    public TakeoverResult takeOver(TakeoverCommand command) {
        validate(command);
        Takeover replay = repository.findByRequest(command.requestId());
        if (replay != null) return new TakeoverResult(replay, true);
        if (!repository.conversationExists(command.tenantId(), command.conversationId())) throw new TakeoverException("会话不属于当前租户");
        if (!repository.acceptedOrClaimed(command)) throw new TakeoverException("接管必须基于已接受邀请或成功领取");
        Takeover requested = new Takeover(UUID.randomUUID(), command.tenantId(), command.conversationId(), command.staffId(),
                command.requestId(), "ACTIVE", Instant.now());
        Takeover actual = repository.createActiveIfAbsent(requested);
        return new TakeoverResult(actual, actual.id().equals(requested.id()));
    }

    private void validate(TakeoverCommand command) {
        if (command == null || blank(command.tenantId()) || command.conversationId() == null || blank(command.staffId()) || command.requestId() == null) throw new TakeoverException("接管命令不完整");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
