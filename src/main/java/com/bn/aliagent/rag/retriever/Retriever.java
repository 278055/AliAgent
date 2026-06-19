package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RetrievalContext;

import java.util.List;

/**
 * 检索器 —— RAG Pipeline 步骤 3
 *
 * <p>从知识库中检索与查询相关的文档片段，
 * 是 RAG Pipeline 中唯一与向量存储/搜索引擎直接交互的组件。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code VectorRetriever}：pgvector 余弦相似度检索（Embedding → Top-K）</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code HybridRetriever}：向量检索 + BM25 关键词检索，融合排序</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface Retriever {

    /**
     * 根据查询从知识库中检索相关片段
     *
     * @param query 查询文本（可能已经过改写）
     * @param ctx   检索参数（topK、相似度阈值等）
     * @return 候选片段列表，按相似度降序排列
     */
    List<RagChunk> retrieve(String query, RetrievalContext ctx);
}
