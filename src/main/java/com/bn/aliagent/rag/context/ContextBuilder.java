package com.bn.aliagent.rag.context;

import com.bn.aliagent.rag.model.RagChunk;

import java.util.List;

/**
 * 上下文构建 —— RAG Pipeline 步骤 5
 *
 * <p>对重排序后的片段执行去重、压缩、Token 预算控制，
 * 最终输出一段紧凑的上下文文本，直接喂给 LLM。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code SimpleContextBuilder}：简单拼接 + 基于内容哈希去重 + 硬截断 2000 字符</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code OptimizedContextBuilder}：智能压缩 + Token Budget 动态控制</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface ContextBuilder {

    /**
     * 将检索片段构建为上下文文本
     *
     * @param query    用户原始查询
     * @param chunks   经过检索和重排后的片段列表
     * @param maxTokens 最大 Token/字符数限制
     * @return 组装好的上下文文本
     */
    String build(String query, List<RagChunk> chunks, int maxTokens);
}
