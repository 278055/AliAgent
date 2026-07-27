package com.bn.aliagent.evaluation.intake;

import java.time.Instant;
import java.util.UUID;

public record IntakeCandidate(UUID eventId, String tenantId, IntakeAnonymizationStatus anonymizationStatus,
                              String canonicalJson, String anonymizationRuleVersion, Instant receivedAt) { }
