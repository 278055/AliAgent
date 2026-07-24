package com.bn.aliagent.orchestration.core;

import com.bn.aliagent.orchestration.routing.Intent;
import com.bn.aliagent.orchestration.routing.RuleFirstIntentRouter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrchestrationServiceTest {
    @Test
    void 重复事件和相同请求只应执行一次() {
        InMemoryExecutionStore store = new InMemoryExecutionStore();
        CountingRunner runner = new CountingRunner();
        OrchestrationService service = new OrchestrationService(store, new RuleFirstIntentRouter(input -> Intent.GENERAL), runner);
        ReplyRequestedV2 first = request(UUID.randomUUID(), UUID.randomUUID());
        ReplyRequestedV2 duplicateRequest = request(UUID.randomUUID(), first.requestId());

        service.accept(first, "你好");
        service.accept(first, "你好");
        service.accept(duplicateRequest, "你好");

        assertEquals(1, runner.calls);
        assertEquals(ExecutionStateMachine.Status.COMPLETED, store.findByRequestId(first.requestId()).orElseThrow().status());
    }

    @Test
    void 恢复时不得重放完成步骤() {
        InMemoryExecutionStore store = new InMemoryExecutionStore();
        CountingRunner runner = new CountingRunner();
        OrchestrationService service = new OrchestrationService(store, new RuleFirstIntentRouter(input -> Intent.GENERAL), runner);
        ReplyRequestedV2 request = request(UUID.randomUUID(), UUID.randomUUID());
        ExecutionRecord record = ExecutionRecord.created(request, Intent.GENERAL);
        store.create(record);
        store.markStepCompleted(record.executionId(), "MODEL");
        store.updateStatus(record.executionId(), ExecutionStateMachine.Status.RETRY_PENDING);

        service.resumeIncomplete();

        assertEquals(0, runner.calls);
        assertEquals(ExecutionStateMachine.Status.COMPLETED, store.find(record.executionId()).orElseThrow().status());
    }

    private static ReplyRequestedV2 request(UUID eventId, UUID requestId) {
        return new ReplyRequestedV2(eventId, "test-p5-a", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), requestId, "trace", Instant.now());
    }

    private static final class CountingRunner implements WorkflowRunner {
        private int calls;
        @Override public void run(ExecutionRecord record, String input) { calls++; }
    }
}
