package com.bn.aliagent.rag.prompt;

import com.bn.aliagent.rag.model.RagContext;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Prompt 组装 —— RAG Pipeline 步骤 6（最终步骤）
 *
 * <p>将检索到的上下文与用户问题组装为发给 LLM 的完整 Prompt，
 * 包含 System Prompt（角色设定 + 知识库指令）和 User Prompt（上下文 + 问题）。</p>
 *
 * <h3>一期实现</h3>
 * <p>{@code BasicPromptBuilder}：固定模板 ——
 * System "你是知识库助手，请基于以下上下文回答..." +
 * User "上下文 + 用户问题"</p>
 *
 * <h3>二期升级</h3>
 * <p>{@code DynamicPromptBuilder}：Citation Forcing（要求引用来源） + 安全过滤</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public interface PromptBuilder {

    /**
     * 基于 RAG 上下文组装完整的 Prompt
     *
     * @param ragContext RAG Pipeline 处理结果（包含原始问题 + 最终上下文文本等）
     * @return 组装好的 Spring AI Prompt 对象，可直接传给 ChatClient
     */
    Prompt build(RagContext ragContext);
}
