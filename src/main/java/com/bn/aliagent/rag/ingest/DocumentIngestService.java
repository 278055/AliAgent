package com.bn.aliagent.rag.ingest;

import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 文档摄入服务 —— 文件上传 → 解析 → 分块 → 向量化 → 入库 全链路
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>{@link TikaDocumentReader} 解析文件，提取纯文本</li>
 *   <li>{@link TokenTextSplitter} 按 800 token 分块（overlap 100）</li>
 *   <li>{@link EmbeddingModel} 对每个分块文本向量化（1024 维）</li>
 *   <li>写入 {@code document} 表（元信息）+ 批量写入 {@code document_chunk} 表（含向量）</li>
 * </ol>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    /** 分块大小（Token 数） */
    private static final int CHUNK_SIZE = 800;

    /** 分块最小字符数（低于此值不单独分块） */
    private static final int MIN_CHUNK_SIZE_CHARS = 100;

    /** 支持的文件类型 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "doc", "md", "txt");

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public DocumentIngestService(EmbeddingModel embeddingModel,
                                  JdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 摄入文档
     *
     * @param file 上传文件
     * @return 摄入结果（文档 ID、名称、分块数等）
     */
    @Transactional
    public Map<String, Object> ingest(MultipartFile file) {
        // ── 0. 校验 ──
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "不支持的文件类型: ." + extension + "，仅支持: " + ALLOWED_EXTENSIONS);
        }

        log.info("开始摄入文档: name={}, size={} bytes, type={}", originalName, file.getSize(), extension);

        try {
            // ── 1. Tika 解析 ──
            DocumentReader reader = new TikaDocumentReader(file.getResource());
            List<org.springframework.ai.document.Document> parsedDocs = reader.read();

            if (parsedDocs.isEmpty()) {
                throw new IllegalStateException("Tika 解析结果为空，文件可能不包含可读文本");
            }

            String fullText = parsedDocs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .reduce("", (a, b) -> a + "\n\n" + b);

            if (fullText.isBlank()) {
                throw new IllegalStateException("文档解析后无有效文本内容");
            }

            log.debug("Tika 解析完成: 原始文档 {} 页, 合并文本 {} 字符",
                    parsedDocs.size(), fullText.length());

            // ── 2. 分块 ──
            TokenTextSplitter splitter = new TokenTextSplitter(
                    CHUNK_SIZE, MIN_CHUNK_SIZE_CHARS, 5, 10_000, true);

            // TokenTextSplitter 对 Document 进行 split
            org.springframework.ai.document.Document fullDocument =
                    new org.springframework.ai.document.Document(fullText);
            List<org.springframework.ai.document.Document> chunks = splitter.split(
                    Collections.singletonList(fullDocument));

            log.debug("分块完成: {} 个原始文档 → {} 个分块", parsedDocs.size(), chunks.size());

            if (chunks.isEmpty()) {
                // 无分块则把全文当作一个块
                chunks = List.of(fullDocument);
            }

            // ── 3. 向量化 ──
            List<String> chunkTexts = chunks.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .toList();
            List<float[]> embeddings = embeddingModel.embed(chunkTexts);

            log.debug("向量化完成: {} 个分块", embeddings.size());

            // ── 4. 写 document 元信息表（JDBC 直写，metadata 需要 ::jsonb 转换）──
            String documentId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO document (id, name, type, size, metadata, created_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, NOW())
                    """, documentId, originalName, extension, file.getSize(), "{}");

            // ── 5. 批量写 document_chunk 表 ──
            String insertSql = """
                    INSERT INTO document_chunk
                        (id, document_id, content, embedding, section_title, page_number, chunk_index, metadata)
                    VALUES
                        (?, ?, ?, ?::vector, ?, ?, ?, ?::jsonb)
                    """;

            List<Object[]> batchArgs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = UUID.randomUUID().toString();
                String content = chunks.get(i).getText();

                // 尝试从 Tika metadata 提取页码和标题
                int pageNum = extractPageNumber(chunks.get(i), i);
                String sectionTitle = "";

                String chunkMetadata = buildChunkMetadata(documentId, originalName, extension, pageNum, i);

                batchArgs.add(new Object[]{
                        chunkId,
                        documentId,
                        content,
                        new PGvector(embeddings.get(i)),
                        sectionTitle,
                        pageNum > 0 ? pageNum : null,
                        i,
                        chunkMetadata
                });
            }

            jdbcTemplate.batchUpdate(insertSql, batchArgs);

            log.info("文档摄入完成: documentId={}, name={}, chunks={}",
                    documentId, originalName, chunks.size());

            // ── 6. 返回结果 ──
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("documentId", documentId);
            result.put("name", originalName);
            result.put("type", extension);
            result.put("size", file.getSize());
            result.put("chunkCount", chunks.size());

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档摄入失败: name={}", originalName, e);
            throw new RuntimeException("文档摄入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Tika 元数据中尝试提取页码
     */
    private int extractPageNumber(org.springframework.ai.document.Document chunk, int defaultPage) {
        Map<String, Object> meta = chunk.getMetadata();
        if (meta == null) return defaultPage;
        // Tika 可能的页码 key
        for (String key : List.of("page_number", "page", "xmpTPg:NPages")) {
            Object val = meta.get(key);
            if (val != null) {
                try {
                    return Integer.parseInt(val.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultPage;
    }

    /**
     * 构建分块元数据 JSON
     */
    private String buildChunkMetadata(String documentId, String documentName, String fileType, int pageNumber, int chunkIndex) {
        return String.format(
                "{\"document_id\":\"%s\",\"document_name\":\"%s\",\"file_type\":\"%s\",\"page_number\":%d,\"chunk_index\":%d}",
                escapeJson(documentId), escapeJson(documentName), escapeJson(fileType), pageNumber, chunkIndex);
    }

    /**
     * 简单的 JSON 字符串转义
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 提取文件扩展名
     */
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
