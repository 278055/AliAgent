package com.bn.aliagent.evaluation.candidate;

/** 支持将审核事实与候选状态在同一持久化边界内写入的仓储。 */
public interface ReviewingCandidateRepository extends CandidateRepository {
    EvaluationCandidate review(CandidateReviewCommand command, EvaluationCandidate reviewed);
}
