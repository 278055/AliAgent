package com.bn.aliagent.rag.rewrite;

import java.util.List;

/**
 * 查询改写 —— RAG Pipeline 步骤 2
 *
 * <p>对用户原始问题进行扩展、改写、多视角拆解，
 * 产生多个查询变体以提高检索召回率。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code NoOpQueryRewriter}：原样透传，返回单元素列表</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code MultiQueryRewriter}：LLM 驱动多查询扩展 + 关键词提取</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface QueryRewriter {

    /**
     * 改写用户原始问题为多个查询
     *
     * @param originalQuery 用户原始问题
     * @return 改写后的查询列表（至少包含原始问题本身）
     */
    List<String> rewrite(String originalQuery);
}
