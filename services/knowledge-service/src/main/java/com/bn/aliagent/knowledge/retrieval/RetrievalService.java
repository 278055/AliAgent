package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import com.bn.aliagent.knowledge.catalog.EmbeddingValidator;
import java.util.List;

public final class RetrievalService {
    private final KeywordRetriever keywordRetriever;
    private final SemanticRetriever semanticRetriever;
    private final Reranker reranker;
    private final QueryEmbedder embedder;

    public RetrievalService(KeywordRetriever keywordRetriever, SemanticRetriever semanticRetriever, Reranker reranker) {
        this(keywordRetriever, semanticRetriever, reranker, query -> new float[EmbeddingValidator.DIMENSION]);
    }

    public RetrievalService(KeywordRetriever keywordRetriever, SemanticRetriever semanticRetriever, Reranker reranker, QueryEmbedder embedder) {
        this.keywordRetriever = keywordRetriever;
        this.semanticRetriever = semanticRetriever;
        this.reranker = reranker;
        this.embedder = embedder;
    }

    public RetrievalResponse retrieve(String query, TrustedKnowledgeRequestContext context, int topK) {
        if (query == null || query.isBlank() || topK < 1 || topK > 100) throw new IllegalArgumentException("检索请求无效");
        float[] embedding = embedder.embed(query);
        EmbeddingValidator.requireDimension(embedding);
        int candidateLimit = Math.min(100, topK * 4);
        List<RetrievalCandidate> candidates = ReciprocalRankFusion.fuse(
                keywordRetriever.retrieve(query, context, candidateLimit),
                semanticRetriever.retrieve(query, embedding, context, candidateLimit));
        if (candidates.isEmpty()) return RetrievalResponse.noGrounding();
        return new RetrievalResponse("", reranker.rerank(query, candidates).stream().limit(topK).toList());
    }
}
