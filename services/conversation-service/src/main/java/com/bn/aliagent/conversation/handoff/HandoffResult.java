package com.bn.aliagent.conversation.handoff;

import java.util.UUID;

public record HandoffResult(UUID requestId, String conversationStatus, String takeoverStatus, String reason) { }
