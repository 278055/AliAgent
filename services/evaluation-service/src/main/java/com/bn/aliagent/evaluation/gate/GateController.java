package com.bn.aliagent.evaluation.gate;

import java.time.Clock;
import java.time.Instant;

/** 发布连接由集成阶段实现；该控制器只负责签发不可伪造的门禁证明。 */
public final class GateController {
    private final GateEvaluationService evaluation;
    private final GateDecisionSigner signer;
    private final Clock clock;

    public GateController(GateEvaluationService evaluation, GateDecisionSigner signer, Clock clock) {
        this.evaluation = evaluation; this.signer = signer; this.clock = clock;
    }

    public IssuedDecision issue(String tenantId, GateDecision.GateTarget target, GatePolicy policy,
            GateResultPort.GateEvaluationResults results, String keyId, long validitySeconds) {
        GateEvaluationService.GateEvaluation outcome = evaluation.evaluate(tenantId, policy, results);
        if (outcome.status() != GateEvaluationService.GateStatus.PASS || !target.tenantId().equals(tenantId) || validitySeconds <= 0) {
            throw new IllegalStateException("门禁未通过，拒绝签发证明");
        }
        Instant issuedAt = clock.instant();
        GateDecision decision = new GateDecision(java.util.UUID.randomUUID(), target, results.baselineDigest(), results.datasetVersion(),
                results.scoringPolicyVersion(), policy.version(), results.evaluationTaskId(), results.resultDigest(), issuedAt, issuedAt.plusSeconds(validitySeconds));
        return new IssuedDecision(decision, signer.sign(decision, keyId));
    }

    public record IssuedDecision(GateDecision decision, GateDecision.GateProof proof) { }
}
