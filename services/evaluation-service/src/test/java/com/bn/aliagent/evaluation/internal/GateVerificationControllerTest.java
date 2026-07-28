package com.bn.aliagent.evaluation.internal;

import com.bn.aliagent.evaluation.gate.GateController;
import com.bn.aliagent.evaluation.gate.GateDecision;
import com.bn.aliagent.evaluation.gate.GatePolicy;
import com.bn.aliagent.evaluation.gate.GateResultPort;
import com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository;
import com.bn.platform.security.ServiceJwtSupport;
import java.time.Instant;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.profiles.active=database",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/postgres?currentSchema=test_p8_gate_verification_api",
        "spring.datasource.username=postgres",
        "spring.datasource.password=123456",
        "spring.flyway.enabled=true",
        "SERVICE_JWT_SECRET=test-service-jwt-secret-must-be-at-least-32-bytes",
        "evaluation.anonymization.key=test-evaluation-anonymization-key-must-be-at-least-32-bytes"})
@AutoConfigureMockMvc
class GateVerificationControllerTest {
    private static final String PATH = "/internal/api/v1/evaluation/gate-proofs:verify";
    private static final String SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";
    @Autowired private MockMvc mockMvc;
    @Autowired private GateController gates;
    @Autowired private JdbcGateDecisionRepository decisions;

    @Test
    void missingServiceJwtIsRejected() throws Exception {
        mockMvc.perform(post(PATH).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedPersistedProofIsRejected() throws Exception {
        String tenantId = "test-p8-gate-verification-" + UUID.randomUUID();
        UUID artifactVersionId = UUID.randomUUID();
        GateDecision.GateTarget target = new GateDecision.GateTarget(tenantId, "PROMPT", artifactVersionId, "manifest-digest");
        GateResultPort.GateEvaluationResults results = new GateResultPort.GateEvaluationResults(tenantId, UUID.randomUUID(),
                "manifest-digest", "baseline", "dataset", "score", true, List.of(), Map.of(), "result", Instant.now());
        GateController.IssuedDecision issued = gates.issue(tenantId, target, new GatePolicy("policy-v1", Map.of(), true), results, "database", 600);
        decisions.revoke(tenantId, issued.proof().proofId(), "test revocation");
        String token = new ServiceJwtSupport(SECRET).issue("gateway-service", "evaluation-service", List.of("POST:" + PATH));
        String body = "{\"tenantId\":\"" + tenantId + "\",\"artifactType\":\"PROMPT\",\"artifactVersionId\":\"" + artifactVersionId
                + "\",\"manifestDigest\":\"manifest-digest\",\"policyVersion\":\"policy-v1\",\"proof\":{\"proofId\":\""
                + issued.proof().proofId() + "\",\"canonicalPayload\":\"" + issued.proof().canonicalPayload().replace("\n", "\\n")
                + "\",\"signature\":\"" + issued.proof().signature() + "\",\"keyId\":\"" + issued.proof().keyId() + "\"}}";

        mockMvc.perform(post(PATH).header("X-Service-Authorization", "Bearer " + token).header("X-Tenant-Id", tenantId)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(""))
                .andExpect(jsonPath("$.data.accepted").value(false))
                .andExpect(jsonPath("$.data.reason").isNotEmpty());
    }

    @AfterAll
    static void dropSchema() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456");
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS test_p8_gate_verification_api CASCADE");
        }
    }
}
