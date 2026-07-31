package com.bn.aliagent.insight.fact;

import java.time.Instant;
import java.util.UUID;

public record RecalculationRequest(String tenantId, UUID factId, Instant occurredAt, LateEventDecision decision) { }
