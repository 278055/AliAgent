package com.bn.aliagent.rag.prompt;

import com.bn.aliagent.rag.model.RagContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 基础 Prompt 构建器 —— 固定模板组装
 *
 * <p>一期实现：使用固定模板将检索上下文和用户问题组装为 Prompt</p>
 *
 * <pre>
 * System: 你是一个知识库助手。请严格基于以下上下文回答问题。
 *         如果上下文中没有相关信息，请如实告知用户。
 *
 * User:   上下文信息：
 *         ---
 *         {context}
 *         ---
 *         用户问题：{question}
 * </pre>
 *
 * <p>二期升级：DynamicPromptBuilder（Citation Forcing + 安全过滤）</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class BasicPromptBuilder implements PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(BasicPromptBuilder.class);

    private static final String SYSTEM_TEMPLATE = """
            你是一个知识库助手。请严格基于以下上下文回答问题。\
            如果上下文中没有相关信息，请如实告知用户。""";

    @Override
    public Prompt build(RagContext ragContext) {
        String context = ragContext.getFinalContext();
        String question = ragContext.getOriginalQuery();

        // System Message：角色设定
        Message systemMessage = new SystemMessage(SYSTEM_TEMPLATE);

        // User Message：上下文 + 用户问题
        String userContent;
        if (context != null && !context.isBlank()) {
            userContent = "上下文信息：\n---\n" + context + "\n---\n用户问题：" + question;
        } else {
            // 无上下文时降级为普通提问
            userContent = question;
        }

        Message userMessage = new UserMessage(userContent);
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        log.debug("Prompt 组装完成: contextLength={}, questionLength={}",
                context != null ? context.length() : 0,
                question != null ? question.length() : 0);

        return prompt;
    }
}
