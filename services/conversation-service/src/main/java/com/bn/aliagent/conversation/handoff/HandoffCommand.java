package com.bn.aliagent.conversation.handoff;

import java.util.UUID;

public record HandoffCommand(String tenantId, UUID conversationId, String staffId, UUID requestId, boolean supervisor, String reason) { }
