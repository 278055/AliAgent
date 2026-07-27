package com.bn.aliagent.evaluation.candidate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 控制器适配层只接收网关注入的可信身份，不信任请求体中的租户或审核人。 */
public final class CandidateController {
    private final CandidateReviewService reviews;
    public CandidateController(CandidateReviewService reviews) { this.reviews = reviews; }
    public EvaluationCandidate review(UUID candidateId, ReviewRequest request, TrustedRequestContext context) {
        return reviews.review(new CandidateReviewCommand(candidateId, context.tenantId(), context.subjectId(), request.action(), request.expected(), request.labels(), request.reason()));
    }
    public record ReviewRequest(ReviewAction action, Map<String, Object> expected, Set<String> labels, String reason) { }
    public record TrustedRequestContext(String tenantId, String subjectId) {
        public TrustedRequestContext { if (tenantId == null || tenantId.isBlank() || subjectId == null || subjectId.isBlank()) throw new SecurityException("缺少可信身份上下文"); }
    }
}
