package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

            KeyPair pair = KeyPairGenerator.getInstance("EC").generateKeyPair();
            GateDecisionSigner signer = new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, pair.getPrivate(), "SHA256withECDSA"));
            JdbcGateDecisionRepository decisions = new JdbcGateDecisionRepository(jdbc);
            GateController controller = new GateController(new GateEvaluationService(), signer, Clock.fixed(NOW, ZoneOffset.UTC), decisions);
            GateDecision.GateTarget target = new GateDecision.GateTarget(TENANT, "PROMPT", UUID.randomUUID(), manifest.digest());
            GateResultPort.GateEvaluationResults results = new GateResultPort.GateEvaluationResults(TENANT, runId, manifest.digest(), "baseline", datasetVersionId.toString(),
                    manifest.scoringPolicyVersion(), true, List.of(), Map.of("intent", new GateResultPort.MetricTotals(0.95, 0.90)), "result", NOW);

            GateController.IssuedDecision issued = controller.issue(TENANT, target, new GatePolicy("gate-v1", Map.of("intent", 0.1), true), results, "key-1", 600);
            GateDecisionVerifier verifier = new GateDecisionVerifier(Map.of("key-1", new GateDecisionVerifier.VerificationKey(pair.getPublic(), "SHA256withECDSA")),
                    decisions.revocations(TENANT), Clock.fixed(NOW, ZoneOffset.UTC));

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_gate_proof WHERE tenant_id = ? AND proof_id = ?", Integer.class, TENANT, issued.proof().proofId()));
            GateDecision.GateProof storedProof = decisions.findProof(TENANT, issued.proof().proofId()).orElseThrow();
            assertTrue(verifier.verify(storedProof, target, "gate-v1").accepted());
            assertFalse(decisions.findProof("other-tenant", issued.proof().proofId()).isPresent());
            decisions.revoke(TENANT, issued.proof().proofId(), "test revoke");
            assertFalse(verifier.verify(storedProof, target, "gate-v1").accepted());
        } finally {
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
