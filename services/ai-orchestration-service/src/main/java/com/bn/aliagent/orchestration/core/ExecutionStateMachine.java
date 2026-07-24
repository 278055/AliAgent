package com.bn.aliagent.orchestration.core;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ExecutionStateMachine {
    public enum Status {
        CREATED, ROUTING, RUNNING, WAITING_USER_INPUT, WAITING_CONFIRMATION,
        RETRY_PENDING, COMPLETED, FAILED, CANCELLED
    }

    private static final Map<Status, Set<Status>> TRANSITIONS = transitions();

    public Status transition(Status from, Status to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("非法执行状态迁移: " + from + " -> " + to);
        }
        return to;
    }

    private static Map<Status, Set<Status>> transitions() {
        Map<Status, Set<Status>> result = new EnumMap<>(Status.class);
        result.put(Status.CREATED, EnumSet.of(Status.ROUTING, Status.CANCELLED, Status.FAILED));
        result.put(Status.ROUTING, EnumSet.of(Status.RUNNING, Status.WAITING_USER_INPUT, Status.WAITING_CONFIRMATION, Status.FAILED, Status.CANCELLED));
        result.put(Status.RUNNING, EnumSet.of(Status.COMPLETED, Status.WAITING_USER_INPUT, Status.WAITING_CONFIRMATION, Status.RETRY_PENDING, Status.FAILED, Status.CANCELLED));
        result.put(Status.WAITING_USER_INPUT, EnumSet.of(Status.RUNNING, Status.CANCELLED, Status.FAILED));
        result.put(Status.WAITING_CONFIRMATION, EnumSet.of(Status.RUNNING, Status.CANCELLED, Status.FAILED));
        result.put(Status.RETRY_PENDING, EnumSet.of(Status.RUNNING, Status.FAILED, Status.CANCELLED));
        return Map.copyOf(result);
    }
}
