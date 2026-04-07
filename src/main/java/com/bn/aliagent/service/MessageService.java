package com.bn.aliagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bn.aliagent.entity.Message;
import com.bn.aliagent.mapper.MessageMapper;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息业务逻辑层
 *
 * 封装对 message 表的增删改查操作，同时负责与 Spring AI 的消息格式转换
 */
@Service
public class MessageService extends ServiceImpl<MessageMapper, Message> {

    /**
     * 保存一条消息到数据库
     *
     * @param conversationId 所属会话 ID
     * @param role           消息角色：user 或 assistant
     * @param content        消息内容
     */
    public void saveMessage(String conversationId, String role, String content) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        this.save(message);
    }

    /**
     * 获取指定会话的所有历史消息
     * 将数据库实体 Message 转换为 Spring AI 的标准 Message 接口格式，
     * 以便后续发送给 ChatClient 构建 prompt
     *
     * @param conversationId 会话 ID
     * @return 按时间升序排列的 Spring AI 消息列表
     */
    public List<org.springframework.ai.chat.messages.Message> getHistoryMessages(String conversationId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        // 按 conversation_id 过滤，并按创建时间升序排列（保持对话顺序）
        wrapper.eq("conversation_id", conversationId).orderByAsc("created_at");
        List<Message> messages = this.list(wrapper);

        // 将实体 Message 转换为 Spring AI 的 UserMessage 或 AssistantMessage
        return messages.stream().map(msg -> {
            if ("user".equals(msg.getRole())) {
                return (org.springframework.ai.chat.messages.Message) new UserMessage(msg.getContent());
            } else {
                return (org.springframework.ai.chat.messages.Message) new AssistantMessage(msg.getContent());
            }
        }).toList();
    }

    /**
     * 查询指定会话的所有原始消息（Entity 格式）
     *
     * @param conversationId 会话 ID
     * @return 按时间升序排列的消息列表
     */
    public List<Message> getMessagesByConversationId(String conversationId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId).orderByAsc("created_at");
        return this.list(wrapper);
    }
}
