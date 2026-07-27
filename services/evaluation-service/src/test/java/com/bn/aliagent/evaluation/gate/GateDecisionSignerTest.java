package com.bn.aliagent.evaluation.gate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GateDecisionSignerTest {
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private GateDecisionSigner signer;
    private GateDecisionVerifier verifier;
    private GateDecision decision;
    private KeyPair pair;

    @BeforeEach
    void setUp() throws Exception {
        pair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        signer = new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, pair.getPrivate(), "SHA256withECDSA"));
        verifier = new GateDecisionVerifier(Map.of("key-1", new GateDecisionVerifier.VerificationKey(pair.getPublic(), "SHA256withECDSA")),
                proofId -> false, Clock.fixed(NOW, ZoneOffset.UTC));
        decision = new GateDecision(UUID.randomUUID(), new GateDecision.GateTarget("tenant-a", "PROMPT", UUID.randomUUID(), "manifest-sha"),
                "baseline-sha", "dataset-v1", "scoring-v1", "policy-v1", UUID.randomUUID(), "result-sha", NOW, NOW.plusSeconds(600));
    }

    @Test
    void 签名证明可被验证() {
        GateDecision.GateProof proof = signer.sign(decision, "key-1");

        assertTrue(verifier.verify(proof, decision.target(), "policy-v1").accepted());
    }

    @Test
    void 篡改内容被拒绝() {
        GateDecision.GateProof proof = signer.sign(decision, "key-1");
        GateDecision.GateProof tampered = new GateDecision.GateProof(proof.proofId(), proof.canonicalPayload().replace("policy-v1", "policy-v2"), proof.signature(), proof.keyId());

        assertFalse(verifier.verify(tampered, decision.target(), "policy-v1").accepted());
    }

    @Test
    void 过期撤销跨租户版本策略不符和未知密钥均被拒绝() {
        GateDecision.GateProof proof = signer.sign(decision, "key-1");
        GateDecision expired = new GateDecision(decision.decisionId(), decision.target(), decision.baselineDigest(), decision.datasetVersion(),
                decision.scoringPolicyVersion(), decision.gatePolicyVersion(), decision.evaluationTaskId(), decision.resultDigest(),
                NOW.minusSeconds(1_200), NOW.minusSeconds(1));
        GateDecision.GateProof expiredProof = signer.sign(expired, "key-1");
        GateDecisionVerifier revoked = new GateDecisionVerifier(Map.of("key-1", new GateDecisionVerifier.VerificationKey(
                pair.getPublic(), "SHA256withECDSA")), proofId -> true, Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(verifier.verify(expiredProof, expired.target(), "policy-v1").accepted());
        assertFalse(verifier.verify(proof, new GateDecision.GateTarget("tenant-b", "PROMPT", decision.target().artifactVersionId(), "manifest-sha"), "policy-v1").accepted());
        assertFalse(verifier.verify(proof, new GateDecision.GateTarget("tenant-a", "PROMPT", UUID.randomUUID(), "manifest-sha"), "policy-v1").accepted());
        assertFalse(verifier.verify(proof, decision.target(), "policy-v2").accepted());
        assertFalse(verifier.verify(new GateDecision.GateProof(proof.proofId(), proof.canonicalPayload(), proof.signature(), "unknown"), decision.target(), "policy-v1").accepted());
        assertFalse(revoked.verify(proof, decision.target(), "policy-v1").accepted());
    }

    @Test
    void 仅通过门禁的结果可以签发证明() {
        GateController controller = new GateController(new GateEvaluationService(), signer, Clock.fixed(NOW, ZoneOffset.UTC));
        GateResultPort.GateEvaluationResults passing = new GateResultPort.GateEvaluationResults("tenant-a", decision.evaluationTaskId(),
                "manifest-sha", "baseline-sha", "dataset-v1", "scoring-v1", true, java.util.List.of(),
                Map.of("intent", new GateResultPort.MetricTotals(0.95, 0.90)), "result-sha", NOW);

        assertTrue(controller.issue("tenant-a", decision.target(), new GatePolicy("policy-v1", Map.of("intent", 0.1), true), passing, "key-1", 600).proof().signature().length() > 0);
        assertThrows(IllegalStateException.class, () -> controller.issue("tenant-a", decision.target(), new GatePolicy("policy-v1", Map.of(), true),
                new GateResultPort.GateEvaluationResults("tenant-a", decision.evaluationTaskId(), "manifest-sha", "baseline-sha", "dataset-v1", "scoring-v1", false, java.util.List.of(), Map.of(), "result-sha", NOW), "key-1", 600));
    }

    @Test
    void 支持RSA签名且拒绝密钥标识不匹配配置() throws Exception {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        GateDecisionSigner rsaSigner = new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey("other-key", rsa.getPrivate(), "SHA256withRSA"));
        GateDecisionVerifier rsaVerifier = new GateDecisionVerifier(Map.of("rsa-key", new GateDecisionVerifier.VerificationKey(rsa.getPublic(), "SHA256withRSA")),
                proofId -> false, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class, () -> rsaSigner.sign(decision, "rsa-key"));

        GateDecisionSigner validRsaSigner = new GateDecisionSigner(keyId -> new GateDecisionSigner.SigningKey(keyId, rsa.getPrivate(), "SHA256withRSA"));
        assertTrue(rsaVerifier.verify(validRsaSigner.sign(decision, "rsa-key"), decision.target(), "policy-v1").accepted());
    }
}
