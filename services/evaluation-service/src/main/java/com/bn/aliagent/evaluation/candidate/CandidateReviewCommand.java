package com.bn.aliagent.evaluation.candidate;
import java.util.Map; import java.util.Set; import java.util.UUID;
public record CandidateReviewCommand(UUID candidateId, String tenantId, String reviewerId, ReviewAction action, Map<String,Object> expected, Set<String> labels, String reason) { }
