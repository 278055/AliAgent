package com.bn.aliagent.orchestration.core;

@FunctionalInterface
public interface WorkflowRunner {
    WorkflowOutcome run(ExecutionRecord record, String input);
}
