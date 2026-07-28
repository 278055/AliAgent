package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.evaluation.gate.GateController;
import com.bn.aliagent.evaluation.gate.GateDecision;
import com.bn.aliagent.evaluation.gate.GateDecisionSigner;
import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import com.bn.aliagent.evaluation.gate.GateEvaluationService;
import com.bn.aliagent.evaluation.gate.GatePolicy;
import com.bn.aliagent.evaluation.gate.GateResultPort;
import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.EvaluationRunService;
import com.bn.aliagent.evaluation.runner.MockReplayRunner;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcRunAndGateIntegrationTest {
    private static final String TENANT = "test-p8-run-gate";
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void persistsMockRunResultsEvidenceAndRevocablePassProof() throws Exception {
        String schema = "test_p8_run_gate_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID datasetId = UUID.randomUUID();
            UUID datasetVersionId = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, ?, 'test-p8-run-gate', 'PUBLISHED')", datasetId, TENANT);
            jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, ?, ?, 1, ?, 'PUBLISHED')",
                    datasetVersionId, TENANT, datasetId, "a".repeat(64));
            EvaluationManifest manifest = manifest(datasetVersionId);
            ReplayFixture fixture = fixture();
            JdbcEvaluationRunRepository runs = new JdbcEvaluationRunRepository(jdbc);
            EvaluationRunService service = new EvaluationRunService((tenant, version) -> List.of(fixture), new MockReplayRunner(), runs);

            UUID runId = service.startMock(TENANT, manifest);

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_run_manifest WHERE tenant_id = ? AND run_id = ? AND manifest_payload::text LIKE '%promptVersionId%'", Integer.class, TENANT, runId));
            assertEquals(manifest.digest(), jdbc.queryForObject("SELECT manifest_digest FROM evaluation_run WHERE tenant_id = ? AND id = ?", String.class, TENANT, runId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_result WHERE tenant_id = ? AND run_id = ?", Integer.class, TENANT, runId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_metric_evidence WHERE tenant_id = ? AND run_id = ?", Integer.class, TENANT, runId));
            assertThrows(Exception.class, () -> jdbc.update("UPDATE evaluation_run_manifest SET model_name = 'changed' WHERE tenant_id = ? AND run_id = ?", TENANT, runId));
            assertThrows(Exception.class, () -> jdbc.update("UPDATE evaluation_run SET manifest_digest = ? WHERE tenant_id = ? AND id = ?", "b".repeat(64), TENANT, runId));
            assertThrows(IllegalStateException.class, () -> runs.storeResult(TENANT, runId, fixture(), new MockReplayRunner().replay(TENANT, manifest, fixture())));
            UUID resultId = jdbc.queryForObject("SELECT id FROM evaluation_result WHERE tenant_id = ? AND run_id = ?", UUID.class, TENANT, runId);
            UUID evidenceId = jdbc.queryForObject("SELECT evidence_id FROM evaluation_metric_evidence WHERE tenant_id = ? AND run_id = ?", UUID.class, TENANT, runId);
            assertThrows(Exception.class, () -> jdbc.update("UPDATE evaluation_result SET evidence_json = '{}'::jsonb WHERE tenant_id = ? AND id = ?", TENANT, resultId));
            assertThrows(Exception.class, () -> jdbc.update("DELETE FROM evaluation_result WHERE tenant_id = ? AND id = ?", TENANT, resultId));
            assertThrows(Exception.class, () -> jdbc.update("UPDATE evaluation_metric_evidence SET metric_value = 2 WHERE tenant_id = ? AND evidence_id = ?", TENANT, evidenceId));
            assertThrows(Exception.class, () -> jdbc.update("DELETE FROM evaluation_metric_evidence WHERE tenant_id = ? AND evidence_id = ?", TENANT, evidenceId));

            KeyPair pair = KeyPairGenerator.getInstance("EC").generateKeyPair();
            GateDecisionSigner signer = new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, pair.getPrivate(), "SHA256withECDSA"));
            JdbcGateDecisionRepository decisions = new JdbcGateDecisionRepository(jdbc);
            GateController controller = new GateController(new GateEvaluationService(), signer, Clock.fixed(NOW, ZoneOffset.UTC), decisions);
            GateDecision.GateTarget target = new GateDecision.GateTarget(TENANT, "PROMPT", UUID.randomUUID(), manifest.digest());
            GateResultPort.GateEvaluationResults results = new GateResultPort.GateEvaluationResults(TENANT, runId, manifest.digest(), "baseline", datasetVersionId.toString(),
                    manifest.scoringPolicyVersion(), true, List.of(), Map.of("intent", new GateResultPort.MetricTotals(0.95, 0.90)), "result", NOW);

            GateController.IssuedDecision issued = controller.issue(TENANT, target, new GatePolicy("gate-v1", Map.of("intent", 0.1), true), results, "key-1", 600);
            GateDecisionVerifier verifier = new GateDecisionVerifier(Map.of("key-1", new GateDecisionVerifier.VerificationKey(pair.getPublic(), "SHA256withECDSA")),
                    decisions.revocations(), Clock.fixed(NOW, ZoneOffset.UTC));

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ?", Integer.class, TENANT, issued.proof().proofId()));
            GateDecision.GateProof storedProof = decisions.findProof(TENANT, issued.proof().proofId()).orElseThrow();
            assertTrue(verifier.verify(storedProof, target, "gate-v1").accepted());
            assertFalse(decisions.findProof("other-tenant", issued.proof().proofId()).isPresent());
            assertFalse(decisions.verify(TENANT, new GateDecision.GateProof(UUID.randomUUID(), storedProof.canonicalPayload(), storedProof.signature(), storedProof.keyId()), target, "gate-v1", verifier));
            assertFalse(decisions.verify("other-tenant", storedProof, target, "gate-v1", verifier));
            assertFalse(decisions.verify(TENANT, new GateDecision.GateProof(storedProof.proofId(), storedProof.canonicalPayload() + "x", storedProof.signature(), storedProof.keyId()), target, "gate-v1", verifier));
            GateController.IssuedDecision rollbackProof = controller.issue(TENANT, new GateDecision.GateTarget(TENANT, "PROMPT", UUID.randomUUID(), manifest.digest()), new GatePolicy("gate-rollback", Map.of(), true), results, "key-1", 600);
            assertThrows(Exception.class, () -> decisions.revoke(TENANT, rollbackProof.proof().proofId(), null));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ? AND revoked_at IS NOT NULL", Integer.class, TENANT, rollbackProof.proof().proofId()));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_revocation revocation JOIN evaluation_gate_proof proof ON revocation.decision_id = proof.decision_id AND revocation.tenant_id = proof.tenant_id WHERE proof.tenant_id = ? AND proof.proof_id = ?", Integer.class, TENANT, rollbackProof.proof().proofId()));
            decisions.revoke(TENANT, issued.proof().proofId(), "test revoke");
            assertFalse(verifier.verify(storedProof, target, "gate-v1").accepted());

            GateDecision.GateTarget failedTarget = new GateDecision.GateTarget(TENANT, "PROMPT", UUID.randomUUID(), manifest.digest());
            assertThrows(GateController.GateRejectedException.class, () -> controller.issue(TENANT, failedTarget, new GatePolicy("gate-fail", Map.of(), true),
                    new GateResultPort.GateEvaluationResults(TENANT, runId, manifest.digest(), "baseline", datasetVersionId.toString(), manifest.scoringPolicyVersion(), false, List.of(), Map.of(), "result", NOW), "key-1", 600));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_decision WHERE tenant_id = ? AND status = 'FAIL'", Integer.class, TENANT));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_proof proof JOIN evaluation_gate_decision decision ON proof.decision_id = decision.id AND proof.tenant_id = decision.tenant_id WHERE proof.tenant_id = ? AND decision.status = 'FAIL'", Integer.class, TENANT));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void completionWaitsForInFlightResultWriteAndThenCompletes() throws Exception {
        String schema = "test_p8_result_lock_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID datasetId = UUID.randomUUID();
            UUID datasetVersionId = UUID.randomUUID();
            UUID runId = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, ?, 'test-p8-result-lock', 'PUBLISHED')", datasetId, TENANT);
            jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, ?, ?, 1, ?, 'PUBLISHED')",
                    datasetVersionId, TENANT, datasetId, "a".repeat(64));
            jdbc.update("INSERT INTO evaluation_run (id, tenant_id, dataset_version_id, manifest_digest, mode, status) VALUES (?, ?, ?, ?, 'MOCK', 'RUNNING')",
                    runId, TENANT, datasetVersionId, "b".repeat(64));

            try (Connection writer = java.sql.DriverManager.getConnection(url, "postgres", "123456")) {
                writer.setAutoCommit(false);
                try (PreparedStatement insert = writer.prepareStatement("INSERT INTO evaluation_result (id, tenant_id, run_id, sample_id, evidence_json) VALUES (?, ?, ?, ?, '{}'::jsonb)")) {
                    insert.setObject(1, UUID.randomUUID());
                    insert.setString(2, TENANT);
                    insert.setObject(3, runId);
                    insert.setObject(4, UUID.randomUUID());
                    insert.executeUpdate();
                }

                JdbcEvaluationRunRepository runs = new JdbcEvaluationRunRepository(jdbc);
                Future<?> completion = executor.submit(() -> runs.complete(TENANT, runId));
                Thread.sleep(200);
                assertFalse(completion.isDone());
                writer.commit();
                completion.get(5, TimeUnit.SECONDS);
            }
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM evaluation_run WHERE tenant_id = ? AND id = ?", String.class, TENANT, runId));
        } finally {
            executor.shutdownNow();
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static EvaluationManifest manifest(UUID datasetVersionId) {
        return new EvaluationManifest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tool-v1", "rule-v1", datasetVersionId, "score-v1", "judge-v1");
    }

    private static ReplayFixture fixture() {
        return new ReplayFixture(UUID.randomUUID(), "test", "hello", "GENERAL", List.of(), List.of(), false, false, false, false, false);
    }
}
