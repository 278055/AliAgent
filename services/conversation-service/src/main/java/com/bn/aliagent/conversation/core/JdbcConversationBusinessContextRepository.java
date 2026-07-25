package com.bn.aliagent.conversation.core;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class JdbcConversationBusinessContextRepository implements ConversationBusinessContextRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationBusinessContextRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ConversationBusinessContext> findByRequestId(String tenantId, String subjectId, UUID requestId) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, linked_order_id, linked_after_sale_id, bound_by_subject_id, binding_source, request_id, version, created_at, updated_at FROM conversation_business_context WHERE tenant_id=? AND bound_by_subject_id=? AND request_id=?",
                (rs, row) -> context(rs), tenantId, subjectId, requestId).stream().findFirst();
    }

    @Override
    public Optional<ConversationBusinessContext> findByConversation(String tenantId, UUID conversationId) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, linked_order_id, linked_after_sale_id, bound_by_subject_id, binding_source, request_id, version, created_at, updated_at FROM conversation_business_context WHERE tenant_id=? AND conversation_id=?",
                (rs, row) -> context(rs), tenantId, conversationId).stream().findFirst();
    }

    @Override
    public ConversationBusinessContext save(ConversationBusinessContext value) {
        jdbc.update("INSERT INTO conversation_business_context (id, tenant_id, conversation_id, linked_order_id, linked_after_sale_id, bound_by_subject_id, binding_source, request_id, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenant_id, conversation_id) DO UPDATE SET linked_order_id=EXCLUDED.linked_order_id, linked_after_sale_id=EXCLUDED.linked_after_sale_id, bound_by_subject_id=EXCLUDED.bound_by_subject_id, binding_source=EXCLUDED.binding_source, request_id=EXCLUDED.request_id, version=conversation_business_context.version + 1, updated_at=EXCLUDED.updated_at",
                value.id(), value.tenantId(), value.conversationId(), value.linkedOrderId(), value.linkedAfterSaleId(),
                value.boundBySubjectId(), value.bindingSource(), value.requestId(), value.version(), timestamp(value.createdAt()), timestamp(value.updatedAt()));
        return findByConversation(value.tenantId(), value.conversationId()).orElseThrow();
    }

    private ConversationBusinessContext context(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConversationBusinessContext(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                rs.getObject(4, Long.class), rs.getString(5), rs.getString(6), rs.getString(7), rs.getObject(8, UUID.class),
                rs.getLong(9), rs.getTimestamp(10).toInstant(), rs.getTimestamp(11).toInstant());
    }
    private Timestamp timestamp(Instant value) { return Timestamp.from(value); }
}
