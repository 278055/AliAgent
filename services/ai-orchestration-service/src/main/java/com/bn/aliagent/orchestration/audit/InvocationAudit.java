package com.bn.aliagent.orchestration.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InvocationAudit(UUID executionId, String tenantId, String toolName, String outcome, long latencyMs,
                              String degradationReason, Map<String, Object> attributes, Instant occurredAt) { }
