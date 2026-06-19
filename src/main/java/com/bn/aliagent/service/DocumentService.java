package com.bn.aliagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bn.aliagent.entity.Document;
import com.bn.aliagent.entity.DocumentChunk;
import com.bn.aliagent.mapper.DocumentChunkMapper;
import com.bn.aliagent.mapper.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库文档业务逻辑层。
 */
@Service
public class DocumentService extends ServiceImpl<DocumentMapper, Document> {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentChunkMapper chunkMapper;

    public DocumentService(DocumentChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    /**
     * 查询所有文档列表，并附带分块数量。
     */
    public List<Document> listAll() {
        List<Document> docs = list(new QueryWrapper<Document>().orderByDesc("created_at"));
        for (Document doc : docs) {
            doc.setChunkCount(chunkMapper.countByDocumentId(doc.getId()));
        }
        return docs;
    }

    /**
     * 根据 ID 获取文档详情，并附带分块数量。
     */
    public Document getDetail(String id) {
        Document doc = getById(id);
        if (doc != null) {
            doc.setChunkCount(chunkMapper.countByDocumentId(id));
        }
        return doc;
    }

    /**
     * 删除文档及其所有分块。
     */
    @Transactional
    public boolean deleteDocument(String id) {
        chunkMapper.deleteByDocumentId(id);
        boolean ok = removeById(id);
        log.info("文档已删除: id={}", id);
        return ok;
    }

    /**
     * 获取文档的所有分块。
     */
    public List<DocumentChunk> getChunks(String documentId) {
        return chunkMapper.findByDocumentId(documentId);
    }

    /**
     * 分页获取文档预览分块。
     */
    public List<DocumentChunk> getPreviewChunks(String documentId, int page, int pageSize, String keyword) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safePageSize;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return chunkMapper.findPreviewChunks(documentId, safeKeyword, safePageSize, offset);
    }

    /**
     * 统计文档预览分块数量。
     */
    public int countPreviewChunks(String documentId, String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        return chunkMapper.countPreviewChunks(documentId, safeKeyword);
    }

    /**
     * 查询指定分块在文档中的序号。
     */
    public Integer getChunkIndex(String documentId, String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            return null;
        }
        return chunkMapper.findChunkIndex(documentId, chunkId);
    }

    /**
     * 获取文档的分块数量。
     */
    public int countChunks(String documentId) {
        return chunkMapper.countByDocumentId(documentId);
    }
}
