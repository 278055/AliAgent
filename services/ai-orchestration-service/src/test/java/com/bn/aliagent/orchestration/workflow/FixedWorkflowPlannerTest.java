package com.bn.aliagent.orchestration.workflow;

import com.bn.aliagent.orchestration.routing.Intent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedWorkflowPlannerTest {
    private final FixedWorkflowPlanner planner = new FixedWorkflowPlanner();

    @Test
    void 应将每种意图映射为固定只读工作流() {
        assertEquals(Workflow.GENERAL, planner.plan(Intent.GENERAL));
        assertEquals(Workflow.RAG, planner.plan(Intent.RAG));
        assertEquals(Workflow.ORDER_QUERY, planner.plan(Intent.ORDER_QUERY));
        assertEquals(Workflow.LOGISTICS_QUERY, planner.plan(Intent.LOGISTICS_QUERY));
        assertEquals(Workflow.HUMAN_HANDOFF, planner.plan(Intent.HUMAN_HANDOFF));
    }

    @Test
    void 转人工工作流不得调用模型或工具() {
        WorkflowDefinition workflow = planner.definition(Workflow.HUMAN_HANDOFF);

        assertFalse(workflow.callsModel());
        assertFalse(workflow.callsReadTool());
        assertTrue(workflow.handsOff());
    }
}
