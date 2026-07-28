package com.bn.aliagent.evaluation.internal;

import com.bn.aliagent.evaluation.gate.GateDecision;
import java.util.UUID;

public record GateVerificationRequest(String tenantId, String artifactType, UUID artifactVersionId,
        String manifestDigest, String policyVersion, Proof proof) {
    public GateDecision.GateTarget target() {
        return new GateDecision.GateTarget(tenantId, artifactType, artifactVersionId, manifestDigest);
    }

    public GateDecision.GateProof gateProof() {
        return new GateDecision.GateProof(proof.proofId(), proof.canonicalPayload(), proof.signature(), proof.keyId());
    }

    public record Proof(UUID proofId, String canonicalPayload, String signature, String keyId) { }
}
