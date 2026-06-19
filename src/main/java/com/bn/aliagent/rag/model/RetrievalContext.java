package com.bn.aliagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索参数 —— 控制 Retriever 的检索行为
 *
 * <p>包含 top-K 值、相似度阈值等可调参数。
 * 一期使用默认值，二期可结合具体场景动态调整。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalContext {

    /** 返回的最大片段数，默认 5 */
    @Builder.Default
    private int topK = 5;

    /** 相似度阈值，低于此值的片段会被过滤，默认 0.5 */
    @Builder.Default
    private float similarityThreshold = 0.5f;

    /**
     * 创建使用默认参数的检索上下文
     */
    public static RetrievalContext defaults() {
        return new RetrievalContext();
    }
}
