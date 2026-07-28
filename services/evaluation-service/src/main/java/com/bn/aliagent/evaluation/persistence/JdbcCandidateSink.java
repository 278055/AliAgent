package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.intake.CandidateSink;
import com.bn.aliagent.evaluation.intake.IntakeCandidate;
import com.bn.aliagent.evaluation.intake.IntakeAnonymizationStatus;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCandidateSink implements CandidateSink {
    private final JdbcTemplate jdbc;
    public JdbcCandidateSink(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void accept(IntakeCandidate value) {
        String status = value.anonymizationStatus() == IntakeAnonymizationStatus.SAFE ? "PENDING_REVIEW" : "QUARANTINED";
        var expiresAt = value.receivedAt().plus(value.anonymizationStatus() == IntakeAnonymizationStatus.SAFE ? Duration.ofDays(30) : Duration.ofDays(7));
        jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)",
                UUID.randomUUID(), value.tenantId(), value.eventId(), value.canonicalJson(), digest(value.canonicalJson()), status, value.anonymizationRuleVersion(), Timestamp.from(expiresAt));
    }
    private static String digest(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
}
