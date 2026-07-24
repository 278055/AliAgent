package com.bn.aliagent.orchestration.workflow;

import com.bn.aliagent.orchestration.routing.Intent;

public final class FixedWorkflowPlanner {
    public Workflow plan(Intent intent) {
        return switch (intent) {
            case GENERAL -> Workflow.GENERAL;
            case RAG -> Workflow.RAG;
            case ORDER_QUERY -> Workflow.ORDER_QUERY;
            case LOGISTICS_QUERY -> Workflow.LOGISTICS_QUERY;
            case HUMAN_HANDOFF -> Workflow.HUMAN_HANDOFF;
        };
    }

    public WorkflowDefinition definition(Workflow workflow) {
        return switch (workflow) {
            case GENERAL -> new WorkflowDefinition(true, false, false);
            case RAG, ORDER_QUERY, LOGISTICS_QUERY -> new WorkflowDefinition(true, true, false);
            case HUMAN_HANDOFF -> new WorkflowDefinition(false, false, true);
        };
    }
}
