package com.bn.aliagent.conversation.messaging;

import java.time.Instant;
import java.util.UUID;

public record HumanCollaborationEvent(UUID eventId, String eventType, String tenantId, UUID conversationId,
                                      UUID requestId, Instant occurredAt, String sender, String content, String status,
                                      UUID authorizationSnapshotId, String subjectId, String subjectType,
                                      String roles, String permissions) { }
