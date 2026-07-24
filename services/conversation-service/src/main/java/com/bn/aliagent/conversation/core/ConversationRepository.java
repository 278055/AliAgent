package com.bn.aliagent.conversation.core;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {
    Conversation create(Conversation value);
    Optional<Conversation> findConversation(UUID id, String tenantId);
    List<Conversation> listConversations(String tenantId, int offset, int limit);
    long countConversations(String tenantId);
    Conversation update(Conversation value);
    void softDelete(UUID id, String tenantId);
    Optional<Message> findUserMessage(String tenantId, String subjectId, UUID conversationId, UUID requestId);
    Optional<Message> findStaffMessage(String tenantId, String subjectId, UUID conversationId, UUID clientMessageId);
    Message appendUserMessage(Message value, String subjectId);
    Message appendStaffMessage(Message value, String subjectId, UUID clientMessageId);
    Message appendAiStreamingMessage(Message value, UUID generationId);
    Optional<Message> findAiGeneration(String tenantId, UUID conversationId, UUID requestId);
    List<Message> listMessages(String tenantId, UUID conversationId, long afterSequence, int limit);
    void enqueue(ReplyRequest value);
    List<ReplyRequest> pendingReplies(int limit);
    void markPublished(UUID eventId);
}
