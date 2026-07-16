package com.bn.aliagent.knowledge.retrieval;

import java.util.List;

public record RetrievalResponse(String message, List<RetrievalCandidate> items) {
    public static RetrievalResponse noGrounding() {
        return new RetrievalResponse("没有可用的知识依据", List.of());
    }
}
