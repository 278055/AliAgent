package com.bn.aliagent.conversation.core;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
public class ConversationService {
    private final ConversationRepository repository;

    public ConversationService(ConversationRepository repository) { this.repository = repository; }

    @Transactional
    public Conversation create(TrustedConversationRequestContext context, String title) {
        Instant now = Instant.now();
        String value = title == null || title.isBlank() ? "New conversation" : title;
        return repository.create(new Conversation(UUID.randomUUID(), context.tenantId(), context.subjectId(), value,
                "HUMAN_ACTIVE", false, now, now));
    }

    public Conversation get(TrustedConversationRequestContext context, UUID id) { return owned(context, id); }

    public List<Conversation> list(TrustedConversationRequestContext context, int page, int pageSize) {
        return repository.listConversations(context.tenantId(), (page - 1) * pageSize, pageSize);
    }

    public long count(TrustedConversationRequestContext context) { return repository.countConversations(context.tenantId()); }

    @Transactional
    public Conversation patch(TrustedConversationRequestContext context, UUID id, String title, Boolean pinned, Boolean closed) {
        Conversation current = owned(context, id);
        return repository.update(new Conversation(current.id(), current.tenantId(), current.ownerSubjectId(),
                title == null ? current.title() : title, Boolean.TRUE.equals(closed) ? "CLOSED" : current.status(),
                pinned == null ? current.pinned() : pinned, current.createdAt(), Instant.now()));
    }

    @Transactional
    public void delete(TrustedConversationRequestContext context, UUID id) {
        owned(context, id);
        repository.softDelete(id, context.tenantId());
    }

    public List<Message> messages(TrustedConversationRequestContext context, UUID id, long after, int pageSize) {
        owned(context, id);
        return repository.listMessages(context.tenantId(), id, after, pageSize);
    }

    @Transactional
    public Message submit(TrustedConversationRequestContext context, UUID conversationId, String content, UUID requestId, String key) {
        ConversationPolicy.requireIdempotencyKey(requestId, key);
        Conversation conversation = owned(context, conversationId);
        if (!conversation.ownerSubjectId().equals(context.subjectId())) {
            throw new ConversationException("TENANT-403-001", "Conversation is not owned by the caller");
        }
        return repository.findUserMessage(context.tenantId(), context.subjectId(), conversationId, requestId).orElseGet(() -> {
            Message message = repository.appendUserMessage(new Message(UUID.randomUUID(), context.tenantId(), conversationId,
                    0, "USER", "TEXT", "PRIVATE", content, "SUBMITTED", requestId, "{}", Instant.now()), context.subjectId());
            repository.enqueue(new ReplyRequest(UUID.randomUUID(), context.tenantId(), conversationId, message.id(), requestId,
                    context.traceId(), Instant.now()));
            return message;
        });
    }

    private Conversation owned(TrustedConversationRequestContext context, UUID id) {
        return repository.findConversation(id, context.tenantId())
                .orElseThrow(() -> new ConversationException("TENANT-403-001", "Conversation is not accessible"));
    }
}
