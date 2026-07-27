package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.gate.GateDecision;
import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcGateDecisionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public JdbcGateDecisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())); }
    public void storeDecision(String tenantId, GateDecision decision, String signature, String keyId) {
        jdbc.update("INSERT INTO evaluation_gate_decision (id, tenant_id, artifact_type, artifact_version_id, manifest_digest, policy_version, status, expires_at, signature, key_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", decision.decisionId(), tenantId, decision.target().artifactType(), decision.target().artifactVersionId(), decision.target().manifestDigest(), decision.gatePolicyVersion(), decision.gateStatus().name(), Timestamp.from(decision.expiresAt()), signature, keyId);
    }
    public void storeProof(String tenantId, GateDecision decision, GateDecision.GateProof proof) {
        jdbc.update("INSERT INTO evaluation_gate_proof (tenant_id, proof_id, decision_id, canonical_payload, canonical_payload_text, signature, key_id, issued_at, expires_at) VALUES (?, ?, ?, to_jsonb(CAST(? AS text)), ?, ?, ?, ?, ?)", tenantId, proof.proofId(), decision.decisionId(), proof.canonicalPayload(), proof.canonicalPayload(), proof.signature(), proof.keyId(), Timestamp.from(decision.issuedAt()), Timestamp.from(decision.expiresAt()));
    }
    public GateDecisionVerifier.RevocationPort revocations(String tenantId) {
        return proofId -> Boolean.TRUE.equals(jdbc.query("SELECT EXISTS (SELECT 1 FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ? AND revoked_at IS NOT NULL)", rs -> rs.next() && rs.getBoolean(1), tenantId, proofId));
    }
    public Optional<GateDecision.GateProof> findProof(String tenantId, UUID proofId) {
        return jdbc.query("SELECT canonical_payload_text, signature, key_id FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ?", rs ->
                rs.next() ? Optional.of(new GateDecision.GateProof(proofId, rs.getString(1), rs.getString(2), rs.getString(3))) : Optional.empty(), tenantId, proofId);
    }
    public boolean verify(String tenantId, GateDecision.GateProof proof, GateDecision.GateTarget target, String policyVersion, GateDecisionVerifier verifier) {
        return findProof(tenantId, proof.proofId()).filter(stored -> stored.canonicalPayload().equals(proof.canonicalPayload())
                && stored.signature().equals(proof.signature()) && stored.keyId().equals(proof.keyId()))
                .filter(stored -> verifier.verify(stored, target, policyVersion).accepted()).isPresent();
    }
    public void revoke(String tenantId, UUID proofId, String reason) {
        transactions.executeWithoutResult(status -> {
            UUID decisionId = jdbc.query("SELECT decision_id FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ? FOR UPDATE", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, proofId);
            if (decisionId == null) throw new SecurityException("unknown gate proof");
            Instant now = Instant.now();
            jdbc.update("UPDATE evaluation_gate_proof SET revoked_at = ?, revocation_reason = ? WHERE tenant_id = ? AND proof_id = ? AND revoked_at IS NULL", Timestamp.from(now), reason, tenantId, proofId);
            jdbc.update("INSERT INTO evaluation_gate_revocation (id, tenant_id, decision_id, reason, revoked_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT (tenant_id, decision_id) DO NOTHING", UUID.randomUUID(), tenantId, decisionId, reason, Timestamp.from(now));
        });
    }
}
