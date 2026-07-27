package com.bn.aliagent.evaluation;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bn.aliagent.evaluation.gate.GateController;
import com.bn.aliagent.evaluation.gate.GateDecisionSigner;
import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import com.bn.aliagent.evaluation.gate.GateDecision;
import com.bn.aliagent.evaluation.gate.GatePolicy;
import com.bn.aliagent.evaluation.gate.GateResultPort;
import com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository;
import com.bn.aliagent.evaluation.runner.EvaluationRunService;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "spring.profiles.active=database",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres?currentSchema=test_p8_wiring_context",
        "spring.datasource.username=postgres",
        "spring.datasource.password=123456",
        "spring.flyway.enabled=true",
        "SERVICE_JWT_SECRET=test-service-jwt-secret-must-be-at-least-32-bytes",
        "evaluation.anonymization.key=test-evaluation-anonymization-key-must-be-at-least-32-bytes"})
class DatabaseEvaluationWiringIntegrationTest {
    @Autowired private ApplicationContext context;
    @Autowired private EvaluationRunService runs;
    @Autowired private GateController gates;
    @Autowired private JdbcGateDecisionRepository decisions;
    @Autowired private GateDecisionSigner signer;
    @Autowired private GateDecisionVerifier verifier;

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
        assertFalse(decisions.verify(tenant, issued.proof(), target, "policy", verifier));
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456"); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS test_p8_wiring_context CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS test_p8_wiring_placeholder CASCADE");
        }
    }
}
