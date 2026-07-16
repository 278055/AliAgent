package com.bn.aliagent.knowledge.retrieval;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion {
    private static final int RANK_CONSTANT = 60;

    private ReciprocalRankFusion() {
    }

    public static List<RetrievalCandidate> fuse(List<RetrievalCandidate> keyword, List<RetrievalCandidate> semantic) {
        Map<java.util.UUID, RetrievalCandidate> merged = new LinkedHashMap<>();
        add(merged, keyword);
        add(merged, semantic);
        return merged.values().stream().sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed()
                .thenComparing(candidate -> candidate.chunkId().toString())).toList();
    }

    private static void add(Map<java.util.UUID, RetrievalCandidate> merged, List<RetrievalCandidate> candidates) {
        for (int index = 0; index < candidates.size(); index++) {
            RetrievalCandidate candidate = candidates.get(index);
            double score = 1.0d / (RANK_CONSTANT + index + 1);
            merged.merge(candidate.chunkId(), candidate.withScore(score),
                    (current, incoming) -> current.withScore(current.score() + incoming.score()));
        }
    }
}
