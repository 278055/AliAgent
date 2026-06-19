package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RetrievalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;

/**
 * 向量检索器 —— 基于 pgvector 余弦相似度检索
 *
 * <p>一期实现：</p>
 * <ol>
 *   <li>将查询文本交给 {@link VectorStore}（PgVectorStore）</li>
 *   <li>PgVectorStore 内部自动调用 EmbeddingModel 向量化查询</li>
 *   <li>通过 pgvector 的 {@code <=>} 余弦距离算子执行相似度检索</li>
 *   <li>将 Spring AI Document 映射为 {@link RagChunk}</li>
 * </ol>
 *
 * <p>二期升级：HybridRetriever（向量 + BM25 混合检索）</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class VectorRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(VectorRetriever.class);

    private final VectorStore vectorStore;

    public VectorRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RagChunk> retrieve(String query, RetrievalContext ctx) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            // 构建检索请求：PgVectorStore 内部会自动调用 EmbeddingModel 做向量化
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(ctx.getTopK())
                    .similarityThreshold((double) ctx.getSimilarityThreshold())
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);

            log.debug("向量检索完成: query=\"{}\", topK={}, threshold={}, 命中 {} 条",
                    query, ctx.getTopK(), ctx.getSimilarityThreshold(), docs.size());

            return docs.stream()
                    .map(this::toRagChunk)
                    .toList();

        } catch (Exception e) {
            log.error("向量检索异常: query=\"{}\"", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 Spring AI Document 转换为 RagChunk
     *
     * <p>PgVectorStore 返回的 Document 包含：</p>
     * <ul>
     *   <li>{@code id} → 分块主键</li>
     *   <li>{@code content} → 分块文本</li>
     *   <li>{@code score} → 余弦相似度（由 1 - cosine_distance 计算）</li>
     *   <li>{@code metadata} → JSONB 元数据</li>
     * </ul>
     */
    private RagChunk toRagChunk(Document doc) {
        return RagChunk.builder()
                .chunkId(doc.getId())
                .documentId((String) doc.getMetadata().getOrDefault("document_id", ""))
                .content(doc.getText())
                .score(doc.getScore() != null ? doc.getScore().floatValue() : 0.0f)
                .metadata(doc.getMetadata())
                .build();
    }
}
