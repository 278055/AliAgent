package com.bn.aliagent.rag.rerank;

import com.bn.aliagent.rag.model.RagChunk;

import java.util.List;

/**
 * 重排序 —— RAG Pipeline 步骤 4
 *
 * <p>对检索到的候选片段进行精排，提升最相关片段的排名，
 * 确保 LLM 优先关注质量最高的上下文。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code NoOpReranker}：直通，保持检索原始顺序</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code DashScopeReranker}：使用 DashScope Rerank 模型精排 Top-50 → Top-5</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface Reranker {

    /**
     * 对候选片段进行重排序
     *
     * @param query      用户原始查询
     * @param candidates 检索阶段返回的候选片段
     * @return 重排序后的片段列表（数量可能少于输入）
     */
    List<RagChunk> rerank(String query, List<RagChunk> candidates);
}
