package com.bn.aliagent.conversation.streaming;

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
public class JdbcStreamingRepository implements StreamingRepository {
    private final JdbcTemplate jdbc;
    public JdbcStreamingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<StreamingModels.Generation> find(String tenantId, UUID conversationId, UUID generationId) { return query(" WHERE tenant_id = ? AND conversation_id = ? AND generation_id = ?", tenantId, conversationId, generationId).stream().findFirst(); }
    public Optional<StreamingModels.Generation> findByMessage(String tenantId, UUID conversationId, UUID messageId) { return query(" WHERE tenant_id = ? AND conversation_id = ? AND message_id = ?", tenantId, conversationId, messageId).stream().findFirst(); }
    public StreamingModels.Generation save(StreamingModels.Generation value) {
        jdbc.update("INSERT INTO conversation_generation (generation_id, tenant_id, conversation_id, message_id, request_id, message_sequence, status, last_chunk_index, checkpoint_content, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (generation_id) DO UPDATE SET status = EXCLUDED.status, last_chunk_index = EXCLUDED.last_chunk_index, checkpoint_content = EXCLUDED.checkpoint_content, updated_at = EXCLUDED.updated_at",
                value.generationId(), value.tenantId(), value.conversationId(), value.messageId(), value.requestId(), value.sequence(), value.status().name(), value.lastChunkIndex(), value.content(), Timestamp.from(value.updatedAt()));
        return value;
    }
    public List<StreamingModels.Generation> terminalAfter(String tenantId, UUID conversationId, long afterSequence) { return query(" WHERE tenant_id = ? AND conversation_id = ? AND message_sequence > ? AND status <> 'STREAMING' ORDER BY message_sequence", tenantId, conversationId, afterSequence); }
    public List<StreamingModels.Generation> active() { return query(" WHERE status = 'STREAMING'"); }
    private List<StreamingModels.Generation> query(String condition, Object... parameters) {
        return jdbc.query("SELECT generation_id, tenant_id, conversation_id, message_id, request_id, message_sequence, status, last_chunk_index, checkpoint_content, updated_at FROM conversation_generation" + condition,
                (rs, row) -> new StreamingModels.Generation(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getLong(6), StreamingModels.GenerationStatus.valueOf(rs.getString(7)), rs.getInt(8), rs.getString(9), rs.getTimestamp(10).toInstant()), parameters);
    }
}
