package com.bn.aliagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.evaluation.gate.GateController;
import com.bn.aliagent.evaluation.gate.GateDecisionSigner;
import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import com.bn.aliagent.evaluation.gate.GateDecision;
import com.bn.aliagent.evaluation.gate.GatePolicy;
import com.bn.aliagent.evaluation.gate.GateResultPort;
import com.bn.aliagent.evaluation.candidate.CandidateReviewCommand;
import com.bn.aliagent.evaluation.candidate.CandidateReviewService;
import com.bn.aliagent.evaluation.candidate.ReviewAction;
import com.bn.aliagent.evaluation.dataset.EvaluationDatasetService;
import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository;
import com.bn.aliagent.evaluation.persistence.JdbcDatasetSnapshotPort;
import com.bn.aliagent.evaluation.runner.EvaluationRunService;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "spring.profiles.active=database",
        "spring.datasource.username=postgres",
        "spring.datasource.password=123456",
        "spring.flyway.enabled=true",
        "SERVICE_JWT_SECRET=test-service-jwt-secret-must-be-at-least-32-bytes",
        "evaluation.anonymization.key=test-evaluation-anonymization-key-must-be-at-least-32-bytes"})
class DatabaseEvaluationWiringIntegrationTest {
    private static final String SCHEMA = "test_p8_wiring_" + UUID.randomUUID().toString().replace('-', '_');
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        try (var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + SCHEMA);
    }

    @Autowired private ApplicationContext context;
    @Autowired private EvaluationRunService runs;
    @Autowired private GateController gates;
    @Autowired private JdbcGateDecisionRepository decisions;
    @Autowired private GateDecisionSigner signer;
    @Autowired private GateDecisionVerifier verifier;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CandidateReviewService candidates;
    @Autowired private EvaluationDatasetService datasets;

    @Test
    void databaseProfileWiresDurableRunAndGateServices() {
        assertNotNull(runs);
        assertNotNull(gates);
        assertNotNull(signer);
        assertNotNull(verifier);
        assertInstanceOf(JdbcGateDecisionRepository.class, decisions);
    }

    @Test
    void persistedPassProofIsAcceptedThenRejectedAfterTenantScopedRevocation() {
        String tenant = "test-p8-wiring";
        GateDecision.GateTarget target = new GateDecision.GateTarget(tenant, "PROMPT", java.util.UUID.randomUUID(), "manifest");
        GateResultPort.GateEvaluationResults results = new GateResultPort.GateEvaluationResults(tenant, java.util.UUID.randomUUID(), "manifest", "baseline", "dataset", "score", true, List.of(), Map.of(), "result", Instant.now());
        GateController.IssuedDecision issued = gates.issue(tenant, target, new GatePolicy("policy", Map.of(), true), results, "database", 600);

        assertTrue(decisions.verify(tenant, issued.proof(), target, "policy", verifier));
        decisions.revoke(tenant, issued.proof().proofId(), "test");
        assertFalse(verifier.verify(issued.proof(), target, "policy").accepted());
        assertFalse(decisions.verify(tenant, issued.proof(), target, "policy", verifier));
    }

    @Test
    void revocationIsIsolatedWhenTenantsHaveProofsWithTheSameProofId() {
        String tenantA = "test-p8-revocation-a";
        String tenantB = "test-p8-revocation-b";
        GateDecision.GateTarget targetA = new GateDecision.GateTarget(tenantA, "PROMPT", UUID.randomUUID(), "manifest-a");
        GateDecision.GateTarget targetB = new GateDecision.GateTarget(tenantB, "PROMPT", UUID.randomUUID(), "manifest-b");
        GateController.IssuedDecision proofA = gates.issue(tenantA, targetA, new GatePolicy("policy", Map.of(), true), results(tenantA, "manifest-a"), "database", 600);
        GateController.IssuedDecision proofB = gates.issue(tenantB, targetB, new GatePolicy("policy", Map.of(), true), results(tenantB, "manifest-b"), "database", 600);

        jdbc.update("UPDATE evaluation_gate_proof SET proof_id = ? WHERE tenant_id = ? AND proof_id = ?", proofA.proof().proofId(), tenantB, proofB.proof().proofId());
        decisions.revoke(tenantB, proofA.proof().proofId(), "test");

        assertTrue(verifier.verify(proofA.proof(), targetA, "policy").accepted());
        assertFalse(verifier.verify(new GateDecision.GateProof(proofA.proof().proofId(), proofB.proof().canonicalPayload(), proofB.proof().signature(), proofB.proof().keyId()), targetB, "policy").accepted());
    }

    @Test
    void databaseProfileRunsPublishedSnapshotAndPersistsResultAndEvidence() throws Exception {
        String tenant = "test-p8-durable";
        UUID candidateId = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, ?, ?, ?::jsonb, ?, 'PENDING_REVIEW', 'test', now() + interval '1 day')",
                candidateId, tenant, UUID.randomUUID(), "{\"input\":\"test-p8 durable sample\"}", "digest");
        candidates.review(new CandidateReviewCommand(candidateId, tenant, "test-reviewer", ReviewAction.ACCEPT,
                Map.of("intent", "ORDER_STATUS"), java.util.Set.of("test"), "accepted"));
        var draft = datasets.createDraft(tenant, "test-p8-durable-draft");
        datasets.addCandidate(tenant, draft.id(), candidateId);
        var published = datasets.publish(tenant, draft.id(), false, null);
        EvaluationManifest manifest = new EvaluationManifest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "tool-v1", "rule-v1", published.id(), "score-v1", "judge-v1");
        String requiredCitation = "mock://" + manifest.knowledgeVersionId();
        jdbc.update("UPDATE evaluation_sample_snapshot SET snapshot_payload = ?::jsonb WHERE tenant_id = ? AND dataset_version_id = ?",
                "{\"allowedTools\":[\"mall.order.read\"],\"citationRequirements\":[\"" + requiredCitation + "\"],\"expectedHumanHandoff\":true}", tenant, published.id());

        var fixture = new JdbcDatasetSnapshotPort(jdbc).published(tenant, published.id()).get(0);
        assertEquals(List.of("mall.order.read"), fixture.allowedTools());
        assertEquals(List.of(requiredCitation), fixture.requiredCitations());
        assertTrue(fixture.expectsHumanHandoff());

        UUID runId = runs.startMock(tenant, manifest);

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_result WHERE tenant_id = ? AND run_id = ?", Integer.class, tenant, runId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_metric_evidence WHERE tenant_id = ? AND run_id = ?", Integer.class, tenant, runId));
        String result = jdbc.queryForObject("SELECT evidence_json::text FROM evaluation_result WHERE tenant_id = ? AND run_id = ?", String.class, tenant, runId);
        String evidence = jdbc.queryForObject("SELECT evidence_payload::text FROM evaluation_metric_evidence WHERE tenant_id = ? AND run_id = ?", String.class, tenant, runId);
        assertTrue(result.contains("mall.order.read"));
        assertTrue(result.contains(requiredCitation));
        assertTrue(JSON.readTree(result).path("humanHandoff").asBoolean());
        assertTrue(evidence.contains("mall.order.read"));
        assertTrue(evidence.contains(requiredCitation));
        assertTrue(JSON.readTree(evidence).path("result").path("humanHandoff").asBoolean());
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM evaluation_run WHERE tenant_id = ? AND id = ?", String.class, tenant, runId));
    }

    private static GateResultPort.GateEvaluationResults results(String tenant, String manifest) {
        return new GateResultPort.GateEvaluationResults(tenant, UUID.randomUUID(), manifest, "baseline", "dataset", "score", true, List.of(), Map.of(), "result", Instant.now());
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456"); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }
}
