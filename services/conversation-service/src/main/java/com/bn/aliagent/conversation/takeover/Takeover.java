package com.bn.aliagent.conversation.takeover;

import java.time.Instant;
import java.util.UUID;

public record Takeover(UUID id, String tenantId, UUID conversationId, String staffId, UUID requestId, String status, Instant createdAt) { }
