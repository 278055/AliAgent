package com.bn.aliagent.orchestration.governance;

import java.util.UUID;

public record ExecutionVersionSet(UUID promptVersionId, UUID workflowVersionId, UUID modelVersionId, UUID ruleVersionId) { }
