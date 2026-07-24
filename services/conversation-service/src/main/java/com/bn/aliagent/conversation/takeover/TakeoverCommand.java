package com.bn.aliagent.conversation.takeover;

import java.util.UUID;

public record TakeoverCommand(String tenantId, UUID conversationId, String staffId, UUID requestId, String evidence) { }
