package com.bn.aliagent.evaluation.candidate;
import java.util.*;
public interface CandidateRepository { void save(EvaluationCandidate c); EvaluationCandidate require(String tenant,UUID id); Collection<EvaluationCandidate> all();
 final class InMemory implements CandidateRepository { private final Map<UUID,EvaluationCandidate> values=new LinkedHashMap<>(); public void save(EvaluationCandidate c){values.put(c.id(),c);} public EvaluationCandidate require(String t,UUID id){var c=values.get(id);if(c==null)throw new IllegalArgumentException("候选不存在");if(!c.tenantId().equals(t))throw new SecurityException("跨租户访问被拒绝");return c;} public Collection<EvaluationCandidate> all(){return values.values();} } }
