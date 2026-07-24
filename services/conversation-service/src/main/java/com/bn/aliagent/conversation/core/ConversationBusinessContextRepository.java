package com.bn.aliagent.conversation.core;

import java.util.Optional;
import java.util.UUID;

public interface ConversationBusinessContextRepository {
    Optional<ConversationBusinessContext> findByRequestId(String tenantId, String subjectId, UUID requestId);
    Optional<ConversationBusinessContext> findByConversation(String tenantId, UUID conversationId);
    ConversationBusinessContext save(ConversationBusinessContext context);
}
