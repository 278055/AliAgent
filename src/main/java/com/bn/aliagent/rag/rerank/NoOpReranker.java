package com.bn.aliagent.rag.rerank;

import com.bn.aliagent.rag.model.RagChunk;

import java.util.Collections;
import java.util.List;

/**
 * 空操作重排序 —— 保持检索原始顺序不变
 *
 * <p>一期最简实现：不对候选片段做任何重排序，
 * 直接将检索结果原样返回。</p>
 *
 * <p>二期升级：DashScopeReranker（Top-50 → 精排 Top-5）</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class NoOpReranker implements Reranker {

    @Override
    public List<RagChunk> rerank(String query, List<RagChunk> candidates) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        return candidates;
    }
}
