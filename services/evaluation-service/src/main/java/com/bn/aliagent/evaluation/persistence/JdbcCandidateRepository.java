package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.candidate.CandidateRepository;
import com.bn.aliagent.evaluation.candidate.CandidateStatus;
import com.bn.aliagent.evaluation.candidate.EvaluationCandidate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCandidateRepository implements CandidateRepository {
    private final JdbcTemplate jdbc;
    public JdbcCandidateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void save(EvaluationCandidate candidate) { throw new UnsupportedOperationException("候选审核持久化待后续适配"); }
    @Override public EvaluationCandidate require(String tenantId, UUID id) {
        return jdbc.query("SELECT tenant_id, status, anonymized_body::text, body_digest FROM evaluation_candidate WHERE id = ? AND tenant_id = ?", rs -> {
            if (!rs.next()) throw new SecurityException("跨租户访问被拒绝");
            String body = rs.getString(3);
            return new EvaluationCandidate(id, rs.getString(1), CandidateStatus.valueOf(rs.getString(2)), Optional.ofNullable(body), rs.getString(4), java.time.Instant.now(), Map.of(), java.util.Set.of(), "");
        }, id, tenantId);
    }
    @Override public Collection<EvaluationCandidate> all() { return java.util.List.of(); }
}
