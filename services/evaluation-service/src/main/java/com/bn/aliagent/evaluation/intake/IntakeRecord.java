package com.bn.aliagent.evaluation.intake;

import java.time.Instant;
import java.util.UUID;

public record IntakeRecord(UUID eventId, String tenantId, String eventType, int eventVersion, String contentDigest,
                           IntakeStatus status, Instant updatedAt) { }
