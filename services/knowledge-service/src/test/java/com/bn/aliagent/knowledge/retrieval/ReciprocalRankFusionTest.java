package com.bn.aliagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {
    @Test
    void 应按固定Rrf公式融合两路候选() {
        RetrievalCandidate shared = candidate("00000000-0000-0000-0000-000000000001");
        RetrievalCandidate keywordOnly = candidate("00000000-0000-0000-0000-000000000002");
        RetrievalCandidate semanticOnly = candidate("00000000-0000-0000-0000-000000000003");

        List<RetrievalCandidate> result = ReciprocalRankFusion.fuse(
                List.of(shared, keywordOnly), List.of(semanticOnly, shared));

        assertEquals(shared.chunkId(), result.get(0).chunkId());
        assertEquals(1.0d / 61 + 1.0d / 62, result.get(0).score(), 0.0000001d);
        assertEquals(semanticOnly.chunkId(), result.get(1).chunkId());
        assertEquals(keywordOnly.chunkId(), result.get(2).chunkId());
    }

    @Test
    void 分数相同必须按切片Id稳定排序() {
        RetrievalCandidate later = candidate("00000000-0000-0000-0000-000000000002");
        RetrievalCandidate earlier = candidate("00000000-0000-0000-0000-000000000001");

        List<RetrievalCandidate> result = ReciprocalRankFusion.fuse(List.of(later), List.of(earlier));

        assertEquals(earlier.chunkId(), result.get(0).chunkId());
        assertEquals(later.chunkId(), result.get(1).chunkId());
    }

    private RetrievalCandidate candidate(String chunkId) {
        return new RetrievalCandidate(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000020"), UUID.fromString(chunkId), "内容", 0.9d);
    }
}
