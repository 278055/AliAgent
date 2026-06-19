package com.bn.aliagent.agent;

import com.bn.aliagent.entity.Conversation;
import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RagContext;
import com.bn.aliagent.rag.pipeline.RAGPipeline;
import com.bn.aliagent.service.ConversationService;
import com.bn.aliagent.service.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 智能代理核心类。
 */
@Component
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final String DEFAULT_TITLE = "新对话";
    private static final int MAX_TITLE_LENGTH = 24;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private RAGPipeline ragPipeline;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式对话。
     */
    public Flux<String> chat(String conversationId, String message) {
        conversationService.getOrCreate(conversationId);
        List<Message> historyMessages = messageService.getHistoryMessages(conversationId);
        messageService.saveMessage(conversationId, "user", message);

        RagContext ragContext = ragPipeline.execute(message, conversationId);

        Flux<String> flux;
        String sourcesJson = null;

        if (ragContext != null && ragContext.getPrompt() != null) {
            log.info("RAG 增强模式: query=\"{}\", chunks={}", message,
                    ragContext.getReranked() != null ? ragContext.getReranked().size() : 0);
            flux = chatClient.prompt()
                    .messages(historyMessages)
                    .messages(ragContext.getPrompt().getInstructions())
                    .stream()
                    .content();
            sourcesJson = buildSourcesJson(ragContext);
        } else {
            log.debug("普通对话模式: query=\"{}\"", message);
            flux = chatClient.prompt()
                    .messages(historyMessages)
                    .user(message)
                    .stream()
                    .content();
        }

        final String finalSourcesJson = sourcesJson;
        final StringBuilder accumulated = new StringBuilder();

        return flux
                .doOnNext(accumulated::append)
                .doFinally(signalType -> {
                    String fullResponse = accumulated.toString();
                    if (!fullResponse.isEmpty()) {
                        if (finalSourcesJson != null) {
                            messageService.saveMessage(conversationId, "assistant", fullResponse, finalSourcesJson);
                        } else {
                            messageService.saveMessage(conversationId, "assistant", fullResponse);
                        }
                        generateTitleIfNeeded(conversationId, message, fullResponse);
                    }
                });
    }

    /**
     * 新建会话并发起流式对话。
     */
    public Flux<String> chat(String message) {
        return chat(java.util.UUID.randomUUID().toString(), message);
    }

    /**
     * 仅当会话仍是默认标题时，使用 AI 生成一句短标题。
     */
    private void generateTitleIfNeeded(String conversationId, String userMessage, String assistantMessage) {
        try {
            Conversation conversation = conversationService.getById(conversationId);
            if (conversation == null || !isDefaultTitle(conversation.getTitle())) {
                return;
            }

            String title = generateConversationTitle(userMessage, assistantMessage);
            if (title == null || title.isBlank()) {
                title = fallbackTitle(userMessage);
            }

            conversationService.updateTitle(conversationId, normalizeTitle(title));
        } catch (Exception e) {
            log.warn("生成会话标题失败: conversationId={}", conversationId, e);
            String fallback = fallbackTitle(userMessage);
            if (!fallback.isBlank()) {
                conversationService.updateTitle(conversationId, fallback);
            }
        }
    }

    /**
     * 调用模型生成会话标题。
     */
    private String generateConversationTitle(String userMessage, String assistantMessage) {
        String clippedUserMessage = clip(userMessage, 500);
        String clippedAssistantMessage = clip(assistantMessage, 800);

        String prompt = """
                请根据下面这轮对话生成一个简洁的中文会话标题。
                要求：
                1. 只输出标题本身，不要解释。
                2. 不要使用引号、冒号、句号。
                3. 不超过 12 个中文字符，或不超过 24 个英文字符。
                4. 标题要具体，不要写“新对话”“聊天总结”。

                用户：%s

                助手：%s
                """.formatted(clippedUserMessage, clippedAssistantMessage);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private boolean isDefaultTitle(String title) {
        if (title == null) {
            return true;
        }
        String trimmed = title.trim();
        return trimmed.isEmpty()
                || DEFAULT_TITLE.equals(trimmed)
                || "鏂板璇?".equals(trimmed);
    }

    private String fallbackTitle(String userMessage) {
        return normalizeTitle(userMessage);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        String normalized = title
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[\"'“”‘’。.!！?？:：]+$", "")
                .replaceAll("^标题[:：]\\s*", "")
                .trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            normalized = normalized.substring(0, MAX_TITLE_LENGTH);
        }
        return normalized;
    }

    private String clip(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    /**
     * 将 RAG 检索到的来源片段序列化为 JSON。
     */
    private String buildSourcesJson(RagContext ctx) {
        List<RagChunk> chunks = ctx.getReranked() != null ? ctx.getReranked() : ctx.getCandidates();
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> sourceList = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            Map<String, Object> source = new HashMap<>();
            source.put("chunkId", chunk.getChunkId());
            source.put("documentId", chunk.getDocumentId());
            source.put("content", chunk.getContent());
            source.put("score", chunk.getScore());
            if (chunk.getMetadata() != null) {
                source.put("documentName", chunk.getMetadata().getOrDefault("document_name", ""));
                source.put("documentId", chunk.getMetadata().getOrDefault("document_id", chunk.getDocumentId()));
                source.put("pageNumber", chunk.getMetadata().getOrDefault("page_number", ""));
                source.put("sectionTitle", chunk.getMetadata().getOrDefault("section_title", ""));
            }
            sourceList.add(source);
        }

        try {
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("sources", sourceList);
            return objectMapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            log.error("RAG sources 序列化失败", e);
            return null;
        }
    }
}
