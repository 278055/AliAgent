package com.bn.aliagent.knowledge.retrieval;

import java.util.UUID;

public record RetrievalCandidate(UUID documentId, UUID versionId, UUID chunkId, String content, double score) {
    public RetrievalCandidate withScore(double value) {
        return new RetrievalCandidate(documentId, versionId, chunkId, content, value);
    }
}
