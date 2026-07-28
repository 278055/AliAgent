package com.bn.aliagent.evaluation.internal;

import com.bn.aliagent.evaluation.gate.GateDecisionVerifier;
import com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class GateVerificationController {
    private final ObjectProvider<JdbcGateDecisionRepository> decisions;
    private final ObjectProvider<GateDecisionVerifier> verifier;

    public GateVerificationController(ObjectProvider<JdbcGateDecisionRepository> decisions,
            ObjectProvider<GateDecisionVerifier> verifier) {
        this.decisions = decisions;
        this.verifier = verifier;
    }

    @PostMapping(path = "/internal/api/v1/evaluation/gate-proofs:verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public GateVerificationResponse verify(@RequestHeader("X-Tenant-Id") String trustedTenantId,
            @RequestBody GateVerificationRequest request) {
        if (request == null || !trustedTenantId.equals(request.tenantId()) || !complete(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid gate verification request");
        }
        JdbcGateDecisionRepository repository = decisions.getIfAvailable();
        GateDecisionVerifier gateVerifier = verifier.getIfAvailable();
        if (repository == null || gateVerifier == null) {
            return GateVerificationResponse.rejected("persisted gate verification is unavailable");
        }
        try {
            return repository.verify(trustedTenantId, request.gateProof(), request.target(), request.policyVersion(), gateVerifier)
                    ? GateVerificationResponse.accepted() : GateVerificationResponse.rejected("gate proof was not accepted");
        } catch (RuntimeException exception) {
            return GateVerificationResponse.rejected("gate proof verification failed");
        }
    }

    private static boolean complete(GateVerificationRequest request) {
        return nonBlank(request.tenantId()) && nonBlank(request.artifactType()) && request.artifactVersionId() != null
                && nonBlank(request.manifestDigest()) && nonBlank(request.policyVersion()) && request.proof() != null
                && request.proof().proofId() != null && nonBlank(request.proof().canonicalPayload())
                && nonBlank(request.proof().signature()) && nonBlank(request.proof().keyId());
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    public record GateVerificationResponse(int code, String message, Data data) {
        static GateVerificationResponse accepted() {
            return new GateVerificationResponse(200, "", new Data(true, ""));
        }

        static GateVerificationResponse rejected(String reason) {
            return new GateVerificationResponse(200, "", new Data(false, reason));
        }

        public record Data(boolean accepted, String reason) { }
    }
}
