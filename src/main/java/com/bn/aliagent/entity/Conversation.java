package com.bn.aliagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 会话实体类，对应数据库中的 conversation 表
 *
 * 记录每一个独立的对话会话，包含会话标题、创建时间和更新时间
 */
@Data
@TableName("conversation")
public class Conversation {

    /**
     * 会话唯一标识符（UUID）
     * 由应用层在插入时自动赋值
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 会话标题，默认为"新对话"
     */
    private String title;

    /**
     * 记录插入数据库时的时间
     * 由 MyMetaObjectHandler 自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录每次更新数据库时的时间
     * 由 MyMetaObjectHandler 或数据库触发器自动更新
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 软删除标志，0=未删除，1=已删除；MP 会自动在所有查询末尾拼接 WHERE deleted = 0
     */
    @TableLogic
    private Integer deleted = 0;

    /**
     * 置顶标志，0=未置顶，1=已置顶
     */
    private Integer pinned = 0;
}
