package com.bn.aliagent.evaluation.gate;

import com.bn.aliagent.evaluation.persistence.JdbcGateDecisionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** 仅 PASS 决定可以签发并保存 Gate Proof；FAIL 决定仍会审计保存。 */
public final class GateController {
    private final GateEvaluationService evaluation;
    private final GateDecisionSigner signer;
    private final Clock clock;
    private final JdbcGateDecisionRepository decisions;
    public GateController(GateEvaluationService evaluation, GateDecisionSigner signer, Clock clock) { this(evaluation, signer, clock, null); }
    public GateController(GateEvaluationService evaluation, GateDecisionSigner signer, Clock clock, JdbcGateDecisionRepository decisions) {
        this.evaluation = evaluation; this.signer = signer; this.clock = clock; this.decisions = decisions;
    }
    @Transactional
    public IssuedDecision issue(String tenantId, GateDecision.GateTarget target, GatePolicy policy, GateResultPort.GateEvaluationResults results, String keyId, long validitySeconds) {
        GateEvaluationService.GateEvaluation outcome = evaluation.evaluate(tenantId, policy, results);
        if (!target.tenantId().equals(tenantId) || validitySeconds <= 0) throw new IllegalStateException("gate target is invalid");
        Instant issuedAt = clock.instant();
        GateDecision decision = new GateDecision(java.util.UUID.randomUUID(), target, results.baselineDigest(), results.datasetVersion(), results.scoringPolicyVersion(), policy.version(), results.evaluationTaskId(), results.resultDigest(), outcome.status(), issuedAt, issuedAt.plusSeconds(validitySeconds));
        if (outcome.status() != GateEvaluationService.GateStatus.PASS) {
            if (decisions != null) decisions.storeDecision(tenantId, decision, "", keyId);
            throw new IllegalStateException("gate did not pass");
        }
        GateDecision.GateProof proof = signer.sign(decision, keyId);
        if (decisions != null) { decisions.storeDecision(tenantId, decision, proof.signature(), proof.keyId()); decisions.storeProof(tenantId, decision, proof); }
        return new IssuedDecision(decision, proof);
    }
    public record IssuedDecision(GateDecision decision, GateDecision.GateProof proof) { }
}
