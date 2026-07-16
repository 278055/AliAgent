package com.bn.aliagent.knowledge.api;

import com.bn.aliagent.knowledge.catalog.KnowledgeCatalogRepository;
import com.bn.aliagent.knowledge.ingestion.IngestionInfrastructureConfiguration;
import com.bn.aliagent.knowledge.ingestion.IngestionTaskMessage;
import com.bn.aliagent.knowledge.storage.KnowledgeObjectStorage;
import com.bn.aliagent.knowledge.storage.ObjectKeyFactory;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("database")
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
    private final JdbcTemplate jdbc;
    private final KnowledgeObjectStorage storage;
    private final RabbitTemplate rabbit;
    private final KnowledgeCatalogRepository catalog;
    private final ObjectKeyFactory objectKeys = new ObjectKeyFactory();

    public KnowledgeController(JdbcTemplate jdbc, KnowledgeObjectStorage storage, RabbitTemplate rabbit, KnowledgeCatalogRepository catalog) {
        this.jdbc = jdbc; this.storage = storage; this.rabbit = rabbit; this.catalog = catalog;
    }

    @PostMapping(path = "/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public Map<String, Object> upload(HttpServletRequest request, @RequestPart("file") MultipartFile file) throws Exception {
        TrustedKnowledgeRequestContext context = TrustedKnowledgeRequestContext.require(request);
        String tenantId = context.tenantId(), subjectId = context.subjectId(), traceId = context.traceId();
        UUID documentId = UUID.randomUUID(), versionId = UUID.randomUUID(), taskId = UUID.randomUUID(), eventId = UUID.randomUUID();
        String key = objectKeys.create(tenantId, file.getOriginalFilename());
        storage.put(key, file.getInputStream(), file.getSize(), file.getContentType());
        jdbc.update("INSERT INTO knowledge_document (id, tenant_id, object_key, original_filename, media_type, content_length, checksum_sha256, uploaded_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", documentId, tenantId, key, file.getOriginalFilename(), file.getContentType(), file.getSize(), sha256(file.getBytes()), subjectId);
        jdbc.update("INSERT INTO knowledge_version (id, tenant_id, document_id, version_number, state, created_by) VALUES (?, ?, ?, 1, 'PROCESSING', ?)", versionId, tenantId, documentId, subjectId);
        jdbc.update("INSERT INTO ingestion_task (id, tenant_id, version_id, state, event_id, trace_id) VALUES (?, ?, ?, 'PENDING', ?, ?)", taskId, tenantId, versionId, eventId, traceId);
        IngestionTaskMessage message = new IngestionTaskMessage(eventId.toString(), 1, tenantId, traceId, taskId.toString());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { rabbit.convertAndSend(IngestionInfrastructureConfiguration.QUEUE, message); }
        });
        return Map.of("code", 200, "message", "", "data", Map.of("documentId", documentId, "versionId", versionId, "taskId", taskId));
    }

    @GetMapping("/ingestion-tasks/{taskId}")
    public Map<String, Object> task(HttpServletRequest request, @PathVariable UUID taskId) {
        String tenantId = TrustedKnowledgeRequestContext.require(request).tenantId();
        try {
            Map<String, Object> task = jdbc.queryForMap("SELECT id, state, failure_diagnostic, created_at, updated_at FROM ingestion_task WHERE id = ? AND tenant_id = ?", taskId, tenantId);
            return Map.of("code", 200, "message", "", "data", task);
        } catch (DataAccessException exception) {
            throw new KnowledgeResourceNotFoundException();
        }
    }

    @PostMapping("/versions/{versionId}/publish")
    public Map<String, Object> publish(HttpServletRequest request, @PathVariable UUID versionId) {
        String tenantId = TrustedKnowledgeRequestContext.require(request).tenantId();
        catalog.publish(versionId, tenantId);
        return Map.of("code", 200, "message", "", "data", Map.of("versionId", versionId, "state", "PUBLISHED"));
    }

    private String sha256(byte[] content) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
}
