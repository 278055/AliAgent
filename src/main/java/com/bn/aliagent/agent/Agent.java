package com.bn.aliagent.agent;

import com.bn.aliagent.service.ConversationService;
import com.bn.aliagent.service.MessageService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 智能代理核心类
 *
 * 负责接收用户消息、构建对话上下文、调用通义千问大模型、持久化对话记录
 * 支持流式输出（SSE），前端可实时看到逐字生成效果
 */
@Component
public class Agent {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    /**
     * 流式对话（核心方法）
     *
     * 1. 确保会话记录在数据库中存在
     * 2. 加载该会话的历史消息作为上下文
     * 3. 用户消息立即持久化到数据库
     * 4. 以 Flux<String> 流式返回 AI 生成的每个文本片段
     * 5. 流结束后将完整 AI 回复持久化到数据库
     *
     * @param conversationId 会话 ID
     * @param message       用户当前输入的消息
     * @return Flux<String> — AI 回复的文本片段流（每个片段可直接 SSE 推送给前端）
     */
    public Flux<String> chat(String conversationId, String message) {
        // 1. 确保会话记录已存在
        conversationService.getOrCreate(conversationId);

        // 2. 加载历史消息
        List<Message> historyMessages = messageService.getHistoryMessages(conversationId);

        // 3. 先保存用户消息（同步，发生在流开始之前）
        messageService.saveMessage(conversationId, "user", message);

        // 4. 流式调用大模型，返回每个文本片段的 Flux
        Flux<String> flux = chatClient.prompt()
                .messages(historyMessages)
                .user(message)
                .stream()
                .content();  // Flux<String>

        // 5. 流结束后保存完整 AI 回复（通过 doOnComplete 回调，不阻塞数据流）
        final StringBuilder accumulated = new StringBuilder();

        flux = flux
                .doOnNext(chunk -> accumulated.append(chunk))
                .doOnComplete(() -> {
                    String fullResponse = accumulated.toString();
                    messageService.saveMessage(conversationId, "assistant", fullResponse);
                });

        return flux;
    }

    /**
     * 新建会话并发起流式对话
     *
     * @param message 用户消息
     * @return Flux<String> 流式回复片段
     */
    public Flux<String> chat(String message) {
        return chat(java.util.UUID.randomUUID().toString(), message);
    }
}
