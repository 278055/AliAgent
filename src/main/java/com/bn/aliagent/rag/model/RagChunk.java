package com.bn.aliagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 检索片段 —— RAG Pipeline 中检索/重排阶段的数据载体
 *
 * <p>每次向量检索或关键词检索返回的最小信息单元，
 * 包含片段内容、来源文档、相似度评分及可扩展的元数据。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunk {

    /** 片段唯一标识（通常对应 document_chunk.id） */
    private String chunkId;

    /** 所属文档 ID */
    private String documentId;

    /** 片段文本内容 */
    private String content;

    /** 相似度分数（向量检索为 cosine 距离，关键词检索为 BM25 得分） */
    private float score;

    /** 可扩展元数据：文件名、章节标题、页码、分块序号等 */
    private Map<String, Object> metadata;
}
