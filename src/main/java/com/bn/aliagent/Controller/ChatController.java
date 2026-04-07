package com.bn.aliagent.Controller;

import com.bn.aliagent.agent.Agent;
import com.bn.aliagent.entity.Conversation;
import com.bn.aliagent.entity.Message;
import com.bn.aliagent.service.ConversationService;
import com.bn.aliagent.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 对话接口控制器
 *
 * 提供 HTTP API 供前端或客户端调用 AI 对话能力
 * 支持传入 conversationId 维持多轮对话上下文
 * 支持流式输出（SSE），前端可实时看到逐字生成效果
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private Agent agent;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    /**
     * 流式对话接口（GET 方法，SSE）
     *
     * @param message        用户输入的消息文本
     * @param conversationId 会话 ID（可选），用于维持多轮对话上下文
     *                         如果不传或为空，则自动生成一个新的会话 ID
     * @return Flux<ServerSentEvent<String>> — SSE 流，每个事件 data 为 AI 回复的一个文本片段
     *
     * 前端可通过 EventSource 或 fetch + ReadableStream 消费此接口
     *
     * 示例：
     *   GET /api/chat/stream?message=你好
     *   GET /api/chat/stream?message=继续&conversationId=xxx-xxx-xxx
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam("message") String message,
                                    @RequestParam(value = "conversationId", required = false) String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = java.util.UUID.randomUUID().toString();
        }
        return agent.chat(conversationId, message);
    }

    /**
     * 对话接口（GET 方法，同步版本，保留向后兼容）
     *
     * @param message        用户输入的消息文本
     * @param conversationId 会话 ID（可选）
     * @return AI 助手的完整回复文本
     *
     * 示例：
     *   GET /api/chat?message=你好
     *   GET /api/chat?message=继续&conversationId=xxx-xxx-xxx
     */
    @GetMapping
    public String chat(@RequestParam("message") String message,
                       @RequestParam(value = "conversationId", required = false) String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = java.util.UUID.randomUUID().toString();
        }
        // 同步方式：聚合计 Flux 后返回完整字符串
        return agent.chat(conversationId, message).collectList()
                .map(chunks -> String.join("", chunks))
                .block();
    }

    /**
     * 查询所有未删除的会话列表
     *
     * @return 会话列表（按置顶优先、更新时间倒序）
     */
    @GetMapping("/conversations")
    public List<Conversation> listConversations() {
        return conversationService.listActive();
    }

    /**
     * 删除会话（软删除，deleted 标记为 true）
     *
     * @param id 会话 ID
     * @return success
     */
    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable("id") String id) {
        boolean ok = conversationService.deleteConversation(id);
        return Map.of("success", ok);
    }

    /**
     * 更新会话标题
     *
     * @param id    会话 ID
     * @return success
     */
    @PutMapping("/conversations/{id}/title")
    public Map<String, Object> updateTitle(@PathVariable("id") String id,
                                           @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        if (title.isEmpty()) {
            return Map.of("success", false, "error", "标题不能为空");
        }
        if (title.length() > 64) {
            title = title.substring(0, 64);
        }
        boolean ok = conversationService.updateTitle(id, title);
        return Map.of("success", ok);
    }

    /**
     * 查询指定会话的所有消息（带数据库时间戳，供前端渲染）
     *
     * @param id 会话 ID
     * @return 消息列表（按 createdAt 升序）
     */
    @GetMapping("/conversations/{id}/messages")
    public List<Message> getMessages(@PathVariable("id") String id) {
        return messageService.getMessagesByConversationId(id);
    }

    /**
     * 切换置顶状态
     *
     * @param id 会话 ID
     * @return { pinned: true/false }
     */
    @PutMapping("/conversations/{id}/pin")
    public Map<String, Object> togglePin(@PathVariable("id") String id) {
        boolean pinned = conversationService.togglePinned(id);
        return Map.of("pinned", pinned);
    }
}
