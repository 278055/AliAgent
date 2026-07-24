package com.bn.aliagent.orchestration.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionStateMachineTest {

    @Test
    void 应允许创建后进入路由和运行状态() {
        ExecutionStateMachine stateMachine = new ExecutionStateMachine();

        assertEquals(ExecutionStateMachine.Status.ROUTING,
                stateMachine.transition(ExecutionStateMachine.Status.CREATED, ExecutionStateMachine.Status.ROUTING));
        assertEquals(ExecutionStateMachine.Status.RUNNING,
                stateMachine.transition(ExecutionStateMachine.Status.ROUTING, ExecutionStateMachine.Status.RUNNING));
    }

    @Test
    void 应拒绝从已完成状态继续运行() {
        ExecutionStateMachine stateMachine = new ExecutionStateMachine();

        assertThrows(IllegalStateException.class,
                () -> stateMachine.transition(ExecutionStateMachine.Status.COMPLETED, ExecutionStateMachine.Status.RUNNING));
    }

    @Test
    void 应允许可恢复状态重新进入运行() {
        ExecutionStateMachine stateMachine = new ExecutionStateMachine();

        assertDoesNotThrow(() -> stateMachine.transition(
                ExecutionStateMachine.Status.RETRY_PENDING, ExecutionStateMachine.Status.RUNNING));
    }
}
