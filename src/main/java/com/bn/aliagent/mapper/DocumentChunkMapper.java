package com.bn.aliagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bn.aliagent.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档分块数据访问层。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 查询指定文档的所有分块，按分块序号升序排列。
     */
    @Select("SELECT * FROM document_chunk WHERE document_id = #{documentId} ORDER BY chunk_index ASC")
    List<DocumentChunk> findByDocumentId(@Param("documentId") String documentId);

    /**
     * 分页查询文档预览分块，不读取 embedding 向量字段。
     */
    @Select("""
            SELECT id, document_id, content, section_title, page_number,
                   chunk_index, metadata, created_at
            FROM document_chunk
            WHERE document_id = #{documentId}
              AND (COALESCE(#{keyword}, '') = '' OR content ILIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY chunk_index ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<DocumentChunk> findPreviewChunks(@Param("documentId") String documentId,
                                          @Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    /**
     * 统计文档预览分块数量。
     */
    @Select("""
            SELECT COUNT(*)
            FROM document_chunk
            WHERE document_id = #{documentId}
              AND (COALESCE(#{keyword}, '') = '' OR content ILIKE CONCAT('%', #{keyword}, '%'))
            """)
    int countPreviewChunks(@Param("documentId") String documentId,
                           @Param("keyword") String keyword);

    /**
     * 查询指定分块在文档中的序号。
     */
    @Select("""
            SELECT chunk_index
            FROM document_chunk
            WHERE document_id = #{documentId}
              AND id = #{chunkId}
            """)
    Integer findChunkIndex(@Param("documentId") String documentId,
                           @Param("chunkId") String chunkId);

    /**
     * 统计指定文档的分块数量。
     */
    @Select("SELECT COUNT(*) FROM document_chunk WHERE document_id = #{documentId}")
    int countByDocumentId(@Param("documentId") String documentId);

    /**
     * 删除指定文档的所有分块。
     */
    @Delete("DELETE FROM document_chunk WHERE document_id = #{documentId}")
    int deleteByDocumentId(@Param("documentId") String documentId);
}
