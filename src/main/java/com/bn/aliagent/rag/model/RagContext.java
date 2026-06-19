package com.bn.aliagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

/**
 * RAG Pipeline 上下文 —— 六阶段管道中每一步之间传递的状态对象
 *
 * <h3>数据流转</h3>
 * <pre>
 * originalQuery                              ← 用户原始输入
 *   ↓ ① Strategy 判断 → （不通过则直接跳过）
 *   ↓ ② QueryRewriter
 * rewrittenQueries                           ← 改写后的查询列表
 *   ↓ ③ Retriever
 * candidates                                 ← 候选片段（未去重、未排序）
 *   ↓ ④ Reranker
 * reranked                                   ← 重排后的片段
 *   ↓ ⑤ ContextBuilder
 * finalContext                               ← 最终上下文文本（去重 + 压缩 + 截断）
 *   ↓ ⑥ PromptBuilder
 * prompt                                     ← 组装好的 System + User Prompt
 * </pre>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagContext {

    /** 用户原始问题 */
    private String originalQuery;

    /** 改写后的查询列表（一期直接透传） */
    private List<String> rewrittenQueries;

    /** Retriever 输出的候选片段 */
    private List<RagChunk> candidates;

    /** Reranker 重排序后的片段 */
    private List<RagChunk> reranked;

    /** ContextBuilder 输出的最终上下文文本 */
    private String finalContext;

    /** PromptBuilder 输出的组装 Prompt */
    private Prompt prompt;

    /** 可扩展元数据 */
    private Map<String, Object> metadata;
}
