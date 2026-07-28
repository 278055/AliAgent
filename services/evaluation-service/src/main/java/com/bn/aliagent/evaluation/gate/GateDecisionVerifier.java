package com.bn.aliagent.evaluation.gate;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public final class GateDecisionVerifier {
    private final Map<String, VerificationKey> keys;
    private final RevocationPort revocations;
    private final Clock clock;
    public GateDecisionVerifier(Map<String, VerificationKey> keys, RevocationPort revocations, Clock clock) {
        this.keys = Map.copyOf(keys); this.revocations = revocations; this.clock = clock;
    }

    public Verification verify(GateDecision.GateProof proof, GateDecision.GateTarget target, String policyVersion) {
        try {
            VerificationKey key = keys.get(proof.keyId());
            if (key == null || revocations.isRevoked(target.tenantId(), proof.proofId()) || !signatureMatches(proof, key)) return Verification.reject();
            Map<String, String> payload = proof.canonicalPayload().lines().map(line -> line.split("=", 2)).filter(parts -> parts.length == 2)
                    .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1], (left, right) -> left));
            return "PASS".equals(payload.get("gateStatus")) && Instant.parse(payload.get("expiresAt")).isAfter(clock.instant()) && targetMatches(payload, target) && policyVersion.equals(payload.get("gatePolicyVersion"))
                    ? Verification.accept() : Verification.reject();
        } catch (Exception exception) { return Verification.reject(); }
    }
    private boolean signatureMatches(GateDecision.GateProof proof, VerificationKey key) throws Exception {
        Signature verifier = Signature.getInstance(key.algorithm()); verifier.initVerify(key.publicKey());
        verifier.update(proof.canonicalPayload().getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getUrlDecoder().decode(proof.signature()));
    }
    private boolean targetMatches(Map<String, String> value, GateDecision.GateTarget target) {
        return target.tenantId().equals(value.get("tenantId")) && target.artifactType().equals(value.get("artifactType"))
                && target.artifactVersionId().toString().equals(value.get("artifactVersionId")) && target.manifestDigest().equals(value.get("manifestDigest"));
    }
    public interface RevocationPort { boolean isRevoked(String tenantId, java.util.UUID proofId); }
    public record VerificationKey(PublicKey publicKey, String algorithm) { }
    public record Verification(boolean accepted) { static Verification accept() { return new Verification(true); } static Verification reject() { return new Verification(false); } }
}
