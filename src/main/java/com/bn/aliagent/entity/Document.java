package com.bn.aliagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文档实体类，对应数据库中的 document 表
 *
 * <p>纯元信息存储，不含向量和文本内容。
 * 实际向量数据存放在 {@code document_chunk} 表。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@TableName("document")
public class Document {

    /**
     * 文档唯一标识符（UUID）
     * 由应用层在插入时自动赋值
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 文档名称（含扩展名） */
    private String name;

    /** 文件类型（pdf/docx/md/txt） */
    private String type;

    /** 文件大小（字节） */
    private Long size;

    /**
     * 扩展元数据（JSON 字符串）
     * 存储如原始文件名、上传者、自定义标签等
     */
    private String metadata;

    /**
     * 文档创建时间
     * 由 MyMetaObjectHandler 在插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 分块数量（瞬态字段，不映射数据库列）
     * 用于列表展示
     */
    @TableField(exist = false)
    private Integer chunkCount;
}
