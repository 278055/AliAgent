package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.intake.EvaluationEventEnvelope;
import com.bn.aliagent.evaluation.intake.EventInbox;
import com.bn.aliagent.evaluation.intake.IntakeRecord;
import com.bn.aliagent.evaluation.intake.IntakeStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcEventInbox implements EventInbox {
    private final JdbcTemplate jdbc;
    public JdbcEventInbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<IntakeRecord> reserve(EvaluationEventEnvelope event, String digest, Instant now) {
        try {
            jdbc.update("INSERT INTO evaluation_inbox (event_id, tenant_id, event_type, event_version, content_digest, status, processed_at) VALUES (?, ?, ?, ?, ?, 'RESERVED', ?)",
                    event.eventId(), event.tenantId(), event.eventType(), event.eventVersion(), digest, Timestamp.from(now));
            return Optional.of(new IntakeRecord(event.eventId(), event.tenantId(), event.eventType(), event.eventVersion(), digest, IntakeStatus.RESERVED, now));
        } catch (DuplicateKeyException exception) { return Optional.empty(); }
    }
    @Override public void complete(UUID id, Instant now) { update(id, IntakeStatus.COMPLETED, now); }
    @Override public void fail(UUID id, Instant now) { update(id, IntakeStatus.FAILED, now); }
    @Override public IntakeRecord require(UUID id) {
        return jdbc.queryForObject("SELECT event_id, tenant_id, event_type, event_version, content_digest, status, processed_at FROM evaluation_inbox WHERE event_id = ?", (rs, row) ->
                new IntakeRecord(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), IntakeStatus.valueOf(rs.getString(6)), rs.getTimestamp(7).toInstant()), id);
    }
    private void update(UUID id, IntakeStatus status, Instant now) { jdbc.update("UPDATE evaluation_inbox SET status = ?, processed_at = ? WHERE event_id = ?", status.name(), Timestamp.from(now), id); }
}
