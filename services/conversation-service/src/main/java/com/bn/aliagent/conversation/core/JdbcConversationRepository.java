package com.bn.aliagent.conversation.core;

import com.bn.aliagent.conversation.core.ConversationModels.Conversation;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class JdbcConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Conversation create(Conversation value) {
        jdbc.update("INSERT INTO conversation (id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                value.id(), value.tenantId(), value.ownerSubjectId(), value.title(), value.status(), value.pinned(), timestamp(value.createdAt()), timestamp(value.updatedAt()));
        return value;
    }

    @Override
    public Optional<Conversation> findConversation(UUID id, String tenantId) {
        return jdbc.query("SELECT id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at FROM conversation WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                (rs, row) -> new Conversation(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()), id, tenantId).stream().findFirst();
    }

    @Override
    public List<Conversation> listConversations(String tenantId, int offset, int limit) {
        return jdbc.query("SELECT id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at FROM conversation WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY updated_at DESC OFFSET ? LIMIT ?",
                (rs, row) -> new Conversation(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()), tenantId, offset, limit);
    }

    @Override public long countConversations(String tenantId) { return jdbc.queryForObject("SELECT COUNT(*) FROM conversation WHERE tenant_id = ? AND deleted_at IS NULL", Long.class, tenantId); }

    @Override
    public Conversation update(Conversation value) {
        jdbc.update("UPDATE conversation SET title = ?, status = ?, pinned = ?, updated_at = ? WHERE id = ? AND tenant_id = ?", value.title(), value.status(), value.pinned(), timestamp(value.updatedAt()), value.id(), value.tenantId());
        return value;
    }

    @Override
    public void softDelete(UUID id, String tenantId) {
        Timestamp now = timestamp(Instant.now());
        jdbc.update("UPDATE conversation SET deleted_at = ?, updated_at = ? WHERE id = ? AND tenant_id = ?", now, now, id, tenantId);
    }

    @Override
    public Optional<Message> findUserMessage(String tenantId, String subjectId, UUID conversationId, UUID requestId) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, request_id, metadata, created_at FROM message WHERE tenant_id = ? AND sender_subject_id = ? AND conversation_id = ? AND request_id = ?",
                (rs, row) -> message(rs), tenantId, subjectId, conversationId, requestId).stream().findFirst();
    }

    @Override
    public Optional<Message> findStaffMessage(String tenantId, String subjectId, UUID conversationId, UUID clientMessageId) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, request_id, metadata, created_at FROM message WHERE tenant_id = ? AND sender_subject_id = ? AND conversation_id = ? AND client_message_id = ?",
                (rs, row) -> message(rs), tenantId, subjectId, conversationId, clientMessageId).stream().findFirst();
    }

    @Override
    public Message appendUserMessage(Message value, String subjectId) {
        long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM message WHERE tenant_id = ? AND conversation_id = ?", Long.class, value.tenantId(), value.conversationId());
        Message saved = new Message(value.id(), value.tenantId(), value.conversationId(), sequence, value.senderType(), value.messageType(), value.visibility(), value.content(), value.status(), value.requestId(), value.metadata(), value.createdAt());
        jdbc.update("INSERT INTO message (id, tenant_id, conversation_id, sequence, sender_type, sender_subject_id, message_type, visibility, content, status, request_id, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)",
                saved.id(), saved.tenantId(), saved.conversationId(), saved.sequence(), saved.senderType(), subjectId, saved.messageType(), saved.visibility(), saved.content(), saved.status(), saved.requestId(), saved.metadata(), timestamp(saved.createdAt()), timestamp(saved.createdAt()));
        jdbc.update("UPDATE conversation SET updated_at = ? WHERE id = ? AND tenant_id = ?", timestamp(saved.createdAt()), saved.conversationId(), saved.tenantId());
        return saved;
    }

    @Override
    public Message appendStaffMessage(Message value, String subjectId, UUID clientMessageId) {
        long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM message WHERE tenant_id = ? AND conversation_id = ?", Long.class, value.tenantId(), value.conversationId());
        Message saved = new Message(value.id(), value.tenantId(), value.conversationId(), sequence, value.senderType(), value.messageType(), value.visibility(), value.content(), value.status(), value.requestId(), value.metadata(), value.createdAt());
        jdbc.update("INSERT INTO message (id, tenant_id, conversation_id, sequence, sender_type, sender_subject_id, message_type, visibility, content, status, request_id, client_message_id, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)",
                saved.id(), saved.tenantId(), saved.conversationId(), saved.sequence(), saved.senderType(), subjectId, saved.messageType(), saved.visibility(), saved.content(), saved.status(), saved.requestId(), clientMessageId, saved.metadata(), timestamp(saved.createdAt()), timestamp(saved.createdAt()));
        jdbc.update("UPDATE conversation SET updated_at = ? WHERE id = ? AND tenant_id = ?", timestamp(saved.createdAt()), saved.conversationId(), saved.tenantId());
        return saved;
    }

    @Override
    public Message appendAiStreamingMessage(Message value, UUID generationId) {
        long sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM message WHERE tenant_id = ? AND conversation_id = ?", Long.class, value.tenantId(), value.conversationId());
        Message saved = new Message(value.id(), value.tenantId(), value.conversationId(), sequence, value.senderType(), value.messageType(), value.visibility(), value.content(), value.status(), value.requestId(), value.metadata(), value.createdAt());
        jdbc.update("INSERT INTO message (id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, request_id, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)",
                saved.id(), saved.tenantId(), saved.conversationId(), saved.sequence(), saved.senderType(), saved.messageType(), saved.visibility(), saved.content(), saved.status(), saved.requestId(), saved.metadata(), timestamp(saved.createdAt()), timestamp(saved.createdAt()));
        jdbc.update("UPDATE conversation SET updated_at = ? WHERE id = ? AND tenant_id = ?", timestamp(saved.createdAt()), saved.conversationId(), saved.tenantId());
        return saved;
    }

    @Override
    public Optional<Message> findAiGeneration(String tenantId, UUID conversationId, UUID requestId) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, request_id, metadata, created_at FROM message WHERE tenant_id = ? AND conversation_id = ? AND request_id = ? AND sender_type = 'AI'",
                (rs, row) -> message(rs), tenantId, conversationId, requestId).stream().findFirst();
    }

    @Override
    public List<Message> listMessages(String tenantId, UUID conversationId, long afterSequence, int limit) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, request_id, metadata, created_at FROM message WHERE tenant_id = ? AND conversation_id = ? AND sequence > ? ORDER BY sequence LIMIT ?",
                (rs, row) -> message(rs), tenantId, conversationId, afterSequence, limit);
    }

    @Override
    public void enqueue(ReplyRequest value) {
        String payload = "{\"conversationId\":\"" + value.conversationId() + "\",\"messageId\":\"" + value.messageId()
                + "\",\"requestId\":\"" + value.requestId() + "\"}";
        jdbc.update("INSERT INTO conversation_outbox (event_id, tenant_id, conversation_id, event_type, event_version, trace_id, request_id, payload, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)",
                value.eventId(), value.tenantId(), value.conversationId(), "AIReplyRequested", 1, value.traceId(), value.requestId(), payload, timestamp(value.occurredAt()));
    }

    @Override
    public List<ReplyRequest> pendingReplies(int limit) {
        return jdbc.query("SELECT event_id, tenant_id, conversation_id, request_id, trace_id, occurred_at, payload ->> 'messageId' FROM conversation_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT ?",
                (rs, row) -> new ReplyRequest(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        UUID.fromString(rs.getString(7)), rs.getObject(4, UUID.class), rs.getString(5), rs.getTimestamp(6).toInstant()), limit);
    }

    @Override
    public void markPublished(UUID eventId) {
        jdbc.update("UPDATE conversation_outbox SET published_at = ? WHERE event_id = ? AND published_at IS NULL", timestamp(Instant.now()), eventId);
    }

    private Message message(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Message(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11), rs.getTimestamp(12).toInstant());
    }
    private Timestamp timestamp(Instant value) { return Timestamp.from(value); }
}
