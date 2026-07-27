package com.bn.aliagent.evaluation.gate;

import java.time.Instant;
import java.util.UUID;

public record GateDecision(UUID decisionId, GateTarget target, String baselineDigest, String datasetVersion,
        String scoringPolicyVersion, String gatePolicyVersion, UUID evaluationTaskId, String resultDigest,
        Instant issuedAt, Instant expiresAt) {
    public GateDecision {
        if (issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("证明有效期无效");
        }
    }
    public GateDecision withExpiresAt(Instant value) { return new GateDecision(decisionId, target, baselineDigest, datasetVersion,
            scoringPolicyVersion, gatePolicyVersion, evaluationTaskId, resultDigest, issuedAt, value); }

    public record GateTarget(String tenantId, String artifactType, UUID artifactVersionId, String manifestDigest) { }
    public record GateProof(UUID proofId, String canonicalPayload, String signature, String keyId) { }
}
