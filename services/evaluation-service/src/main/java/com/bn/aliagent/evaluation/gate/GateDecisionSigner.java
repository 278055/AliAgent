package com.bn.aliagent.evaluation.gate;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

public final class GateDecisionSigner {
    private final SigningKeyProvider keys;
    public GateDecisionSigner(SigningKeyProvider keys) { this.keys = keys; }

    public GateDecision.GateProof sign(GateDecision decision, String keyId) {
        try {
            SigningKey key = keys.get(keyId);
            if (!keyId.equals(key.keyId())) {
                throw new IllegalArgumentException("签名密钥标识不匹配");
            }
            String payload = canonicalize(decision);
            Signature signer = Signature.getInstance(key.algorithm());
            signer.initSign(key.privateKey());
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return new GateDecision.GateProof(UUID.randomUUID(), payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()), keyId);
        } catch (IllegalArgumentException exception) { throw exception;
        } catch (Exception exception) { throw new IllegalStateException("无法签发门禁证明", exception); }
    }

    static String canonicalize(GateDecision value) {
        GateDecision.GateTarget target = value.target();
        return String.join("\n", "decisionId=" + value.decisionId(), "tenantId=" + target.tenantId(), "artifactType=" + target.artifactType(),
                "artifactVersionId=" + target.artifactVersionId(), "manifestDigest=" + target.manifestDigest(), "baselineDigest=" + value.baselineDigest(),
                "datasetVersion=" + value.datasetVersion(), "scoringPolicyVersion=" + value.scoringPolicyVersion(), "gatePolicyVersion=" + value.gatePolicyVersion(),
                "evaluationTaskId=" + value.evaluationTaskId(), "resultDigest=" + value.resultDigest(), "issuedAt=" + value.issuedAt(), "expiresAt=" + value.expiresAt());
    }

    public interface SigningKeyProvider { SigningKey get(String keyId); }
    public record SigningKey(String keyId, PrivateKey privateKey, String algorithm) { }
}
