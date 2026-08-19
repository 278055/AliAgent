package com.bn.aliagent.insight.persistence;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import com.bn.aliagent.insight.intake.InsightEventInbox;
import com.bn.aliagent.insight.intake.InsightIntakeRecord;
import com.bn.aliagent.insight.intake.IntakeStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC Inbox 只保存摘要和处理状态，绝不写入原始 payload。 */
public final class JdbcInsightEventInbox implements InsightEventInbox {
    private final JdbcTemplate jdbc;
    public JdbcInsightEventInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<InsightIntakeRecord> reserve(InsightEventEnvelope event, String digest, Instant now) {
        try {
            jdbc.update("INSERT INTO insight_event_inbox (tenant_id, producer, event_id, content_digest, status, created_at, processed_at) VALUES (?, ?, ?, ?, 'PROCESSING', ?, ?)", event.tenantId(), event.producer(), event.eventId(), digest, Timestamp.from(now), Timestamp.from(now));
            return Optional.of(new InsightIntakeRecord(event.tenantId(), event.producer(), event.eventId(), digest, IntakeStatus.PROCESSING, now, now));
        } catch (DuplicateKeyException exception) { return Optional.empty(); }
    }
    @Override public void complete(String tenantId, String producer, UUID eventId, Instant now) { update(tenantId, producer, eventId, IntakeStatus.COMPLETED, now); }
    @Override public void fail(String tenantId, String producer, UUID eventId, Instant now) { update(tenantId, producer, eventId, IntakeStatus.FAILED, now); }
    @Override public InsightIntakeRecord require(String tenantId, String producer, UUID eventId) {
        return jdbc.queryForObject("SELECT tenant_id, producer, event_id, content_digest, status, created_at, processed_at FROM insight_event_inbox WHERE tenant_id=? AND producer=? AND event_id=?", (rs, row) -> new InsightIntakeRecord(rs.getString(1), rs.getString(2), UUID.fromString(rs.getString(3)), rs.getString(4), IntakeStatus.valueOf(rs.getString(5)), rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant()), tenantId, producer, eventId);
    }
    private void update(String tenantId, String producer, UUID eventId, IntakeStatus status, Instant now) { jdbc.update("UPDATE insight_event_inbox SET status=?, processed_at=? WHERE tenant_id=? AND producer=? AND event_id=?", status.name(), Timestamp.from(now), tenantId, producer, eventId); }
}
