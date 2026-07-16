package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.List;

@FunctionalInterface
public interface KeywordRetriever {
    List<RetrievalCandidate> retrieve(String query, TrustedKnowledgeRequestContext context, int limit);
}
