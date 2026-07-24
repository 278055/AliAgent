package com.bn.aliagent.orchestration.core;

@FunctionalInterface
public interface WorkflowRunner {
    void run(ExecutionRecord record, String input);
}
