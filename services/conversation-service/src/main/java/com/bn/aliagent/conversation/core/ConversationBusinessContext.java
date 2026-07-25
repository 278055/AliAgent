package com.bn.aliagent.conversation.core;

import java.time.Instant;
import java.util.UUID;

public record ConversationBusinessContext(UUID id, String tenantId, UUID conversationId, Long linkedOrderId,
                                          String linkedAfterSaleId, String boundBySubjectId, String bindingSource,
                                          UUID requestId, long version, Instant createdAt, Instant updatedAt) { }
