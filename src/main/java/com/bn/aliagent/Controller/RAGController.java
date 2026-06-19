package com.bn.aliagent.Controller;

import com.bn.aliagent.entity.Document;
import com.bn.aliagent.entity.DocumentChunk;
import com.bn.aliagent.rag.ingest.DocumentIngestService;
import com.bn.aliagent.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 知识库 RAG 接口控制器。
 */
@RestController
@RequestMapping("/api/rag")
public class RAGController {

    private static final Logger log = LoggerFactory.getLogger(RAGController.class);

    @Autowired
    private DocumentIngestService ingestService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VectorStore vectorStore;

    /**
     * 上传文档，完成解析、分块、向量化和入库。
     */
    @PostMapping("/documents/upload")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Map.of("success", false, "error", "文件不能为空");
        }

        try {
            Map<String, Object> result = ingestService.ingest(file);
            result.put("success", true);
            log.info("文档上传成功: {}", result.get("name"));
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("文档上传参数错误: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        } catch (Exception e) {
            log.error("文档上传失败", e);
            return Map.of("success", false, "error", "文档处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有文档列表，附带分块数量。
     */
    @GetMapping("/documents")
    public List<Document> listDocuments() {
        return documentService.listAll();
    }

    /**
     * 获取文档详情，附带分块数量。
     */
    @GetMapping("/documents/{id}")
    public Document getDocument(@PathVariable("id") String id) {
        Document doc = documentService.getDetail(id);
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    /**
     * 分页获取文档分块预览内容。
     */
    @GetMapping("/documents/{id}/chunks")
    public Map<String, Object> listDocumentChunks(@PathVariable("id") String id,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "activeChunkId", required = false) String activeChunkId) {
        Document doc = documentService.getById(id);
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
        }

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        if (activeChunkId != null && !activeChunkId.isBlank() && (keyword == null || keyword.isBlank())) {
            Integer chunkIndex = documentService.getChunkIndex(id, activeChunkId);
            if (chunkIndex != null) {
                safePage = Math.max(1, (chunkIndex / safePageSize) + 1);
            }
        }

        int total = documentService.countPreviewChunks(id, keyword);
        List<DocumentChunk> items = documentService.getPreviewChunks(id, safePage, safePageSize, keyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", safePage);
        result.put("pageSize", safePageSize);
        result.put("total", total);
        result.put("items", items);
        return result;
    }

    /**
     * 删除文档及其所有分块。
     */
    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable("id") String id) {
        Document doc = documentService.getById(id);
        if (doc == null) {
            return Map.of("success", false, "error", "文档不存在");
        }

        documentService.deleteDocument(id);
        log.info("文档已删除: id={}, name={}", id, doc.getName());
        return Map.of("success", true, "name", doc.getName());
    }

    /**
     * 向量相似度检索。
     */
    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam("q") String q,
                                             @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (q == null || q.isBlank()) {
            return Collections.emptyList();
        }

        log.debug("RAG 检索: q=\"{}\", topK={}", q, topK);

        SearchRequest request = SearchRequest.builder()
                .query(q)
                .topK(topK)
                .similarityThreshold(0.5)
                .build();

        List<org.springframework.ai.document.Document> docs = vectorStore.similaritySearch(request);

        List<Map<String, Object>> results = new ArrayList<>();
        for (org.springframework.ai.document.Document doc : docs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunkId", doc.getId());
            item.put("content", doc.getText());
            item.put("score", Math.round(doc.getScore() * 10000.0) / 10000.0);

            Map<String, Object> meta = doc.getMetadata();
            if (meta != null) {
                item.put("documentId", meta.getOrDefault("document_id", ""));
                item.put("documentName", meta.getOrDefault("document_name", "未知文档"));
                item.put("fileType", meta.getOrDefault("file_type", ""));
                item.put("pageNumber", meta.getOrDefault("page_number", 0));
                item.put("chunkIndex", meta.getOrDefault("chunk_index", 0));
            }

            results.add(item);
        }

        log.debug("RAG 检索完成: 返回 {} 条结果", results.size());
        return results;
    }
}
