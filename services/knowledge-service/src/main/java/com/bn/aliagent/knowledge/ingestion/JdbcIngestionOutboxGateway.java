package com.bn.aliagent.knowledge.ingestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class JdbcIngestionOutboxGateway implements IngestionOutboxGateway {
    private final JdbcTemplate jdbcTemplate;

    public JdbcIngestionOutboxGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void enqueue(IngestionTaskMessage message) {
        jdbcTemplate.update("INSERT INTO ingestion_outbox (event_id, tenant_id, task_id, trace_id, occurred_at) VALUES (?, ?, ?, ?, ?)",
                UUID.fromString(message.eventId()), message.tenantId(), UUID.fromString(message.taskId()), message.traceId(),
                Timestamp.from(Instant.parse(message.occurredAt())));
    }

    @Override
    public List<IngestionTaskMessage> pending(int limit) {
        return jdbcTemplate.query("SELECT event_id, tenant_id, task_id, trace_id, occurred_at FROM ingestion_outbox "
                        + "WHERE published_at IS NULL ORDER BY created_at LIMIT ?",
                (rs, rowNum) -> new IngestionTaskMessage(rs.getString(1), IngestionTaskMessage.EVENT_TYPE, 1,
                        rs.getTimestamp(5).toInstant().toString(), rs.getString(2), rs.getString(4),
                        IngestionTaskMessage.PRODUCER, new IngestionTaskMessage.IngestionPayload(rs.getString(3))), limit);
    }

    @Override
    public void markPublished(String eventId) {
        jdbcTemplate.update("UPDATE ingestion_outbox SET published_at = ? WHERE event_id = ? AND published_at IS NULL",
                Timestamp.from(Instant.now()), UUID.fromString(eventId));
    }
}
