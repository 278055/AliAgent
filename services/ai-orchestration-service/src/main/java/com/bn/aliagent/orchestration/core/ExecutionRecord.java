package com.bn.aliagent.orchestration.core;

import com.bn.aliagent.orchestration.routing.Intent;

import java.util.UUID;

public record ExecutionRecord(UUID executionId, ReplyRequestedV2 request, Intent intent,
                              ExecutionStateMachine.Status status) {
    public static ExecutionRecord created(ReplyRequestedV2 request, Intent intent) {
        return new ExecutionRecord(UUID.randomUUID(), request, intent, ExecutionStateMachine.Status.CREATED);
    }

    public ExecutionRecord withStatus(ExecutionStateMachine.Status value) {
        return new ExecutionRecord(executionId, request, intent, value);
    }
}
