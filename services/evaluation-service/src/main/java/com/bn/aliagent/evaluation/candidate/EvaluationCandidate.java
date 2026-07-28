package com.bn.aliagent.evaluation.candidate;
import java.time.Instant; import java.util.Map; import java.util.Optional; import java.util.UUID;
public record EvaluationCandidate(UUID id,String tenantId,CandidateStatus status,Optional<String> body,String digest,Instant createdAt,Map<String,Object> expected,java.util.Set<String> labels,String reason) {
 public static EvaluationCandidate pending(UUID id,String tenant,String body,String digest,Instant at){return new EvaluationCandidate(id,tenant,CandidateStatus.PENDING_REVIEW,Optional.of(body),digest,at,Map.of(),java.util.Set.of(),"");}
 public static EvaluationCandidate quarantined(UUID id,String tenant,String reason,Instant at){return new EvaluationCandidate(id,tenant,CandidateStatus.QUARANTINED,Optional.empty(),"",at,Map.of(),java.util.Set.of(),reason);}
 }
