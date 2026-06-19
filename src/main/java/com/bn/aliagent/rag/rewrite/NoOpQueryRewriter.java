package com.bn.aliagent.rag.rewrite;

import java.util.Collections;
import java.util.List;

/**
 * 空操作查询改写 —— 原样透传用户查询
 *
 * <p>一期最简实现：不对用户查询做任何改写或扩展，
 * 直接返回包含原始查询的单元素列表。</p>
 *
 * <p>二期升级：MultiQueryRewriter（多查询扩展 + 关键词提取）</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class NoOpQueryRewriter implements QueryRewriter {

    @Override
    public List<String> rewrite(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(originalQuery);
    }
}
