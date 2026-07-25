package com.bn.aliagent.conversation.core;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
public class ConversationBusinessContextService {
    private final ConversationRepository conversations;
    private final ConversationBusinessContextRepository contexts;
    private final OrderOwnershipVerifier orders;

    public ConversationBusinessContextService(ConversationRepository conversations, ConversationBusinessContextRepository contexts,
                                              OrderOwnershipVerifier orders) {
        this.conversations = conversations;
        this.contexts = contexts;
        this.orders = orders;
    }

    @Transactional
    public ConversationBusinessContext bind(TrustedConversationRequestContext context, UUID conversationId, long orderId,
                                            UUID requestId) {
        requireMemberOwner(context, conversationId);
        if (orderId <= 0) throw new ConversationException("CONV-400-003", "orderId must be positive");
        var replay = contexts.findByRequestId(context.tenantId(), context.subjectId(), requestId);
        if (replay.isPresent()) {
            if (!conversationId.equals(replay.get().conversationId()) || orderId != replay.get().linkedOrderId()) {
                throw new ConversationException("CONV-409-002", "Idempotency request does not match business context");
            }
            return replay.get();
        }
        orders.verifyMemberOwnsOrder(context, orderId);
        Instant now = Instant.now();
        return contexts.save(new ConversationBusinessContext(UUID.randomUUID(), context.tenantId(), conversationId, orderId,
                null, context.subjectId(), "MEMBER_ORDER_ENTRY", requestId, 0, now, now));
    }

    private void requireMemberOwner(TrustedConversationRequestContext context, UUID conversationId) {
        if (!"MEMBER".equals(context.subjectType())) {
            throw new ConversationException("AUTH-403-001", "Only MEMBER can bind business context");
        }
        var conversation = conversations.findConversation(conversationId, context.tenantId())
                .orElseThrow(() -> new ConversationException("TENANT-403-001", "Conversation is not accessible"));
        if (!context.subjectId().equals(conversation.ownerSubjectId())) {
            throw new ConversationException("TENANT-403-001", "Conversation is not owned by the caller");
        }
    }
}
