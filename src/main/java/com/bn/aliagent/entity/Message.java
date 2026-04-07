package com.bn.aliagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息实体类，对应数据库中的 message 表
 *
 * 记录会话中的每条消息，包含角色（user/assistant）、消息内容及时间戳
 */
@Data
@TableName("message")
public class Message {

    /**
     * 消息唯一标识符（UUID）
     * 由应用层在插入时自动赋值
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属会话的 ID，关联 conversation 表
     */
    private String conversationId;

    /**
     * 消息发送者角色：user（用户）或 assistant（AI 助手）
     */
    private String role;

    /**
     * 消息的具体文本内容
     */
    private String content;

    /**
     * 消息创建时间
     * 由 MyMetaObjectHandler 在插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
