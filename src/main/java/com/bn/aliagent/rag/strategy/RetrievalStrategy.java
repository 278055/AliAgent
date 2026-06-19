package com.bn.aliagent.rag.strategy;

import com.bn.aliagent.rag.model.ConversationContext;

/**
 * 检索策略 —— RAG Pipeline 步骤 1
 *
 * <p>判断当前用户消息是否需要触发 RAG 检索。
 * 这是整个 Pipeline 的入口闸门 —— 如果返回 false，
 * 后续步骤全部跳过，对话走普通 LLM 流程。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code RuleStrategy}：基于关键词规则（"什么"、"如何"、"政策" 等）判断</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code LLMStrategy}：使用轻量 LLM 做语义级别分类判断</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface RetrievalStrategy {

    /**
     * 判断是否需要触发 RAG 检索
     *
     * @param userMessage 用户当前消息内容
     * @param ctx         对话上下文（会话 ID、标题等）
     * @return true 表示需要检索，false 表示直接走普通对话
     */
    boolean shouldRetrieve(String userMessage, ConversationContext ctx);
}
