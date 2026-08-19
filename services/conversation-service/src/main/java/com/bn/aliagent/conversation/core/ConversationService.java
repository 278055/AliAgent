package com.bn.aliagent.conversation.core;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ConversationService {
    private final ConversationRepository repository;
    private final com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox humanOutbox;

    public ConversationService(ConversationRepository repository) { this(repository, com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox.noop()); }
    public ConversationService(ConversationRepository repository, com.bn.aliagent.conversation.messaging.HumanCollaborationOutbox humanOutbox) {
        this.repository = repository; this.humanOutbox = humanOutbox;
    }

    @Transactional
    public Conversation create(TrustedConversationRequestContext context, String title) {
        Instant now = Instant.now();
        String value = title == null || title.isBlank() ? "New conversation" : title;
        return repository.create(new Conversation(UUID.randomUUID(), context.tenantId(), context.subjectId(), value,
                "AI_ACTIVE", false, now, now));
    }

    public Conversation get(TrustedConversationRequestContext context, UUID id) { return owned(context, id); }

    public List<Conversation> list(TrustedConversationRequestContext context, int page, int pageSize) {
        return repository.listConversations(context.tenantId(), (page - 1) * pageSize, pageSize);
    }

    public long count(TrustedConversationRequestContext context) { return repository.countConversations(context.tenantId()); }

    @Transactional
    public Conversation patch(TrustedConversationRequestContext context, UUID id, String title, Boolean pinned, Boolean closed) {
        Conversation current = owned(context, id);
        Conversation updated = repository.update(new Conversation(current.id(), current.tenantId(), current.ownerSubjectId(),
                title == null ? current.title() : title, Boolean.TRUE.equals(closed) ? "CLOSED" : current.status(),
                pinned == null ? current.pinned() : pinned, current.createdAt(), Instant.now()));
        if (Boolean.TRUE.equals(closed) && !"CLOSED".equals(current.status())) {
            humanOutbox.append("conversation.completed", context.tenantId(), id, context.requestId(), context.subjectId(), null, "CLOSED");
        }
        return updated;
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
        return submitWithGeneration(context, conversationId, content, requestId, key).userMessage();
    }

    @Transactional
    public ConversationModels.Generation submitWithGeneration(TrustedConversationRequestContext context, UUID conversationId, String content, UUID requestId, String key) {
        ConversationPolicy.requireIdempotencyKey(requestId, key);
        Conversation conversation = owned(context, conversationId);
        if (!conversation.ownerSubjectId().equals(context.subjectId())) {
            throw new ConversationException("TENANT-403-001", "Conversation is not owned by the caller");
        }
        if ("CLOSED".equals(conversation.status())) throw new ConversationException("CONV-409-001", "Closed conversation cannot accept messages");
        Message userMessage = repository.findUserMessage(context.tenantId(), context.subjectId(), conversationId, requestId).orElse(null);
        if (userMessage != null) {
            Message aiMessage = repository.findAiGeneration(context.tenantId(), conversationId, requestId).orElse(null);
            return new ConversationModels.Generation(aiMessage == null ? null : generationId(aiMessage), userMessage, aiMessage);
        }
        userMessage = repository.appendUserMessage(new Message(UUID.randomUUID(), context.tenantId(), conversationId,
                0, "USER", "TEXT", "PRIVATE", content, "SUBMITTED", requestId, "{}", Instant.now()), context.subjectId());
        ConversationRepository.CollaborationState collaboration = repository.collaborationState(context.tenantId(), conversationId);
        if (collaboration != null && "HUMAN_ACTIVE".equals(collaboration.status())) {
            if (context.authorizationSnapshotId() != null && collaboration.staffId() != null && !collaboration.staffId().isBlank()) {
                humanOutbox.append("copilot.suggestion.requested.v2", context.tenantId(), conversationId, requestId,
                        collaboration.staffId(), userMessage.content(), "HUMAN_ACTIVE", context.authorizationSnapshotId(),
                        context.subjectId(), context.subjectType(), context.roles(), context.permissions());
            }
            return new ConversationModels.Generation(null, userMessage, null);
        }
        if (!"AI_ACTIVE".equals(conversation.status()) || collaboration != null && !"AI_ACTIVE".equals(collaboration.status())) return new ConversationModels.Generation(null, userMessage, null);
        Message aiMessage = repository.findAiGeneration(context.tenantId(), conversationId, requestId).orElseGet(() -> {
            UUID generationId = UUID.randomUUID();
            return repository.appendAiStreamingMessage(new Message(UUID.randomUUID(), context.tenantId(), conversationId,
                    0, "AI", "TEXT", "PUBLIC", "", "STREAMING", requestId,
                    "{\"generationId\":\"" + generationId + "\"}", Instant.now()), generationId);
        });
        UUID generationId = generationId(aiMessage);
        repository.enqueue(new ReplyRequest(UUID.randomUUID(), 2, context.tenantId(), conversationId, userMessage.id(),
                aiMessage.id(), generationId, requestId, context.traceId(), Instant.now()));
        return new ConversationModels.Generation(generationId, userMessage, aiMessage);
    }

    public java.util.Optional<Message> findGeneration(String tenantId, UUID conversationId, UUID requestId) {
        return repository.findAiGeneration(tenantId, conversationId, requestId);
    }

    @Transactional
    public Message submitStaffMessage(TrustedConversationRequestContext context, UUID conversationId, String content, UUID clientMessageId) {
        ConversationPolicy.requireStaff(context.subjectType());
        ConversationPolicy.requireClientMessageId(clientMessageId);
        if (content == null || content.isBlank()) throw new ConversationException("CONV-400-002", "content is required");
        owned(context, conversationId);
        return repository.findStaffMessage(context.tenantId(), context.subjectId(), conversationId, clientMessageId).orElseGet(() -> {
            Message saved = repository.appendStaffMessage(new Message(UUID.randomUUID(), context.tenantId(), conversationId, 0,
                    "STAFF", "TEXT", "PUBLIC", content, "COMPLETED", null, "{}", Instant.now()), context.subjectId(), clientMessageId);
            humanOutbox.append("conversation.human.first-public-reply", context.tenantId(), conversationId, clientMessageId,
                    context.subjectId(), null, "HUMAN_ACTIVE");
            return saved;
        });
    }

    @Transactional
    public Conversation takeOver(TrustedConversationRequestContext context, UUID conversationId) {
        ConversationPolicy.requireStaff(context.subjectType());
        return transition(context, conversationId, "HUMAN_ACTIVE");
    }

    @Transactional
    public Conversation requestHuman(TrustedConversationRequestContext context, UUID conversationId) {
        if (!"MEMBER".equals(context.subjectType())) throw new ConversationException("AUTH-403-001", "Only MEMBER can request human service");
        Conversation current = owned(context, conversationId);
        if (!current.ownerSubjectId().equals(context.subjectId())) throw new ConversationException("TENANT-403-001", "Conversation is not owned by the caller");
        if ("CLOSED".equals(current.status())) throw new ConversationException("CONV-409-001", "Closed conversation cannot enter human queue");
        Conversation updated = transition(context, conversationId, "WAITING_HUMAN");
        humanOutbox.append("conversation.human.requested", context.tenantId(), conversationId, context.requestId(),
                context.subjectId(), null, "WAITING_HUMAN");
        return updated;
    }

    @Transactional
    public Conversation release(TrustedConversationRequestContext context, UUID conversationId) {
        ConversationPolicy.requireStaff(context.subjectType());
        return transition(context, conversationId, "AI_ACTIVE");
    }

    private Conversation transition(TrustedConversationRequestContext context, UUID conversationId, String status) {
        Conversation current = owned(context, conversationId);
        return repository.update(new Conversation(current.id(), current.tenantId(), current.ownerSubjectId(), current.title(),
                status, current.pinned(), current.createdAt(), Instant.now()));
    }

    private Conversation owned(TrustedConversationRequestContext context, UUID id) {
        return repository.findConversation(id, context.tenantId())
                .orElseThrow(() -> new ConversationException("TENANT-403-001", "Conversation is not accessible"));
    }

    private UUID generationId(Message message) {
        String marker = "\"generationId\":\"";
        int start = message.metadata().indexOf(marker);
        int valueStart = start + marker.length();
        return UUID.fromString(message.metadata().substring(valueStart, message.metadata().indexOf('"', valueStart)));
    }
}
