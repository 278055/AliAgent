package com.bn.aliagent.knowledge.retrieval;

import java.util.List;

public final class NoOpReranker implements Reranker {
    @Override
    public List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates) {
        return candidates;
    }
}
