package com.bn.aliagent.orchestration.core;

import com.bn.aliagent.orchestration.routing.RuleFirstIntentRouter;

public final class OrchestrationService {
    public static final String CONSUMER = "ai-orchestration-service-v2";
    private final ExecutionStore store;
    private final RuleFirstIntentRouter router;
    private final WorkflowRunner runner;
    private final ExecutionStateMachine stateMachine = new ExecutionStateMachine();

    public OrchestrationService(ExecutionStore store, RuleFirstIntentRouter router, WorkflowRunner runner) {
        this.store = store;
        this.router = router;
        this.runner = runner;
    }

    public void accept(ReplyRequestedV2 request, String input) {
        if (!store.claimInbox(request.eventId(), CONSUMER, request.tenantId())) return;
        try {
            ExecutionRecord record = store.findByRequestId(request.requestId()).orElseGet(() -> {
                ExecutionRecord created = ExecutionRecord.created(request, router.route(input));
                return store.create(created) ? created : store.findByRequestId(request.requestId()).orElseThrow();
            });
            run(record, input);
            store.completeInbox(request.eventId(), CONSUMER);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    public void resumeIncomplete() {
        for (ExecutionRecord record : store.findRecoverable()) run(record, "");
    }

    private void run(ExecutionRecord record, String input) {
        ExecutionRecord current = store.find(record.executionId()).orElseThrow();
        if (current.status() == ExecutionStateMachine.Status.CREATED) store.updateStatus(current.executionId(), stateMachine.transition(current.status(), ExecutionStateMachine.Status.ROUTING));
        current = store.find(current.executionId()).orElseThrow();
        if (current.status() == ExecutionStateMachine.Status.ROUTING || current.status() == ExecutionStateMachine.Status.RETRY_PENDING) store.updateStatus(current.executionId(), stateMachine.transition(current.status(), ExecutionStateMachine.Status.RUNNING));
        current = store.find(current.executionId()).orElseThrow();
        if (!store.isStepCompleted(current.executionId(), "MODEL")) {
            runner.run(current, input);
            store.markStepCompleted(current.executionId(), "MODEL");
        }
        current = store.find(current.executionId()).orElseThrow();
        if (current.status() == ExecutionStateMachine.Status.RUNNING) store.updateStatus(current.executionId(), ExecutionStateMachine.Status.COMPLETED);
    }
}
