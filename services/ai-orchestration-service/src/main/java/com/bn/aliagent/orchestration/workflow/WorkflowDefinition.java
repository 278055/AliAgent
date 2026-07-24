package com.bn.aliagent.orchestration.workflow;

public record WorkflowDefinition(boolean callsModel, boolean callsReadTool, boolean handsOff) { }
