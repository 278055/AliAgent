package com.bn.aliagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档分块实体类，对应数据库中的 document_chunk 表
 *
 * <p><b>注意：</b>{@code embedding} 向量字段不在此实体中映射。
 * 向量的写入和查询通过 JdbcTemplate + PGvector 直接操作，
 * 检索由 {@link org.springframework.ai.vectorstore.VectorStore} 统一处理。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@TableName("document_chunk")
public class DocumentChunk {

    /**
     * 分块唯一标识符（UUID）
     * 由应用层在插入时自动赋值
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属文档 ID，关联 document 表 */
    private String documentId;

    /** 分块文本内容 */
    private String content;

    /** 所属章节标题 */
    private String sectionTitle;

    /** 所在页码 */
    private Integer pageNumber;

    /** 分块序号（从 0 开始） */
    private Integer chunkIndex;

    /**
     * 扩展元数据（JSON 字符串）
     * 存储如文档名、文件类型等冗余信息，方便检索时直接展示
     */
    private String metadata;

    /**
     * 分块创建时间
     * 由 MyMetaObjectHandler 在插入时自动填充
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 相似度得分（瞬态字段，不映射数据库列）
     * 检索时由业务层填充
     */
    @TableField(exist = false)
    private Float similarity;
}
