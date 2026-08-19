package com.bn.aliagent.knowledge.insight;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public final class JdbcKnowledgeInsightOutbox implements KnowledgeInsightOutbox {
    private final JdbcTemplate jdbc;
    public JdbcKnowledgeInsightOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void append(String tenantId, String traceId, UUID versionId, String coverageVersion) {
        Timestamp occurredAt = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO knowledge_insight_outbox(event_id,tenant_id,version_id,trace_id,event_type,coverage_version,occurred_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, versionId, traceId, "knowledge.published", coverageVersion, occurredAt);
        jdbc.update("INSERT INTO knowledge_insight_outbox(event_id,tenant_id,version_id,trace_id,event_type,coverage_version,occurred_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId, versionId, traceId, "knowledge.coverage.versioned", coverageVersion, occurredAt);
    }
    @Override public List<KnowledgeInsightEvent> pending(int limit) {
        return jdbc.query("SELECT event_id,tenant_id,trace_id,event_type,occurred_at,version_id,coverage_version FROM knowledge_insight_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT ?",
                (rs, row) -> new KnowledgeInsightEvent(rs.getObject(1, UUID.class), rs.getString(4), rs.getString(2), rs.getString(3), rs.getTimestamp(5).toInstant(), "knowledge-" + rs.getObject(6, UUID.class), rs.getString(7)), limit);
    }
    @Override public void markPublished(UUID eventId) { jdbc.update("UPDATE knowledge_insight_outbox SET published_at=? WHERE event_id=? AND published_at IS NULL", Timestamp.from(Instant.now()), eventId); }
}
