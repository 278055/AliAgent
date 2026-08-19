package com.bn.aliagent.orchestration.insight;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcP9InsightOutbox implements P9InsightOutbox {
    private final JdbcTemplate jdbc;
    public JdbcP9InsightOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void append(String tenantId, String traceId, UUID executionId, String eventType) {
        jdbc.update("INSERT INTO orchestration_insight_outbox(event_id,tenant_id,execution_id,trace_id,event_type,occurred_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, executionId, traceId, eventType, Timestamp.from(Instant.now()));
    }
    @Override public List<P9InsightEvent> pending(int limit) { return jdbc.query("SELECT event_id,event_type,tenant_id,trace_id,occurred_at,execution_id FROM orchestration_insight_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT ?", (rs, row) -> new P9InsightEvent(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), "execution-" + rs.getObject(6, UUID.class)), limit); }
    @Override public void markPublished(UUID eventId) { jdbc.update("UPDATE orchestration_insight_outbox SET published_at=? WHERE event_id=? AND published_at IS NULL", Timestamp.from(Instant.now()), eventId); }
}
