package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.List;

@FunctionalInterface
public interface SemanticRetriever {
    List<RetrievalCandidate> retrieve(String query, float[] embedding, TrustedKnowledgeRequestContext context, int limit);
}
