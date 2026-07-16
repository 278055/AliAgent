package com.bn.aliagent.knowledge.retrieval;

import java.util.List;

@FunctionalInterface
public interface Reranker {
    List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates);
}
