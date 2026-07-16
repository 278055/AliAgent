package com.bn.aliagent.knowledge.ingestion;

import com.pgvector.PGvector;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("database")
public class JdbcIngestionTaskGateway implements IngestionTaskGateway {
    private final JdbcTemplate jdbcTemplate;

    public JdbcIngestionTaskGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean registerConsumption(String eventId, String consumer) {
        try {
            return jdbcTemplate.update("INSERT INTO consumed_event (event_id, consumer, tenant_id) "
                    + "SELECT event_id, ?, tenant_id FROM ingestion_task WHERE event_id = ?", consumer, UUID.fromString(eventId)) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public IngestionSource load(String taskId, String tenantId) {
        return jdbcTemplate.queryForObject("SELECT task.id, task.version_id, task.tenant_id, document.object_key "
                        + "FROM ingestion_task task JOIN knowledge_version version ON version.id = task.version_id "
                        + "JOIN knowledge_document document ON document.id = version.document_id "
                        + "WHERE task.id = ? AND task.tenant_id = ?",
                (rs, rowNum) -> new IngestionSource(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                UUID.fromString(taskId), tenantId);
    }

    @Override
    @Transactional
    public void replaceChunks(String versionId, String tenantId, List<KnowledgeChunk> values) {
        UUID id = UUID.fromString(versionId);
        jdbcTemplate.update("DELETE FROM knowledge_chunk WHERE version_id = ? AND tenant_id = ?", id, tenantId);
        for (KnowledgeChunk value : values) {
            jdbcTemplate.update("INSERT INTO knowledge_chunk (id, tenant_id, version_id, sequence_number, content, embedding) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, id, value.sequence(), value.content(), new PGvector(value.embedding()));
        }
    }

    @Override
    @Transactional
    public void markReadyForReview(String taskId, String tenantId) {
        UUID id = UUID.fromString(taskId);
        jdbcTemplate.update("UPDATE ingestion_task SET state = 'SUCCEEDED', updated_at = ? WHERE id = ? AND tenant_id = ?", Timestamp.from(Instant.now()), id, tenantId);
        jdbcTemplate.update("UPDATE knowledge_version SET state = 'READY_FOR_REVIEW', failure_diagnostic = NULL, updated_at = ? "
                + "WHERE id = (SELECT version_id FROM ingestion_task WHERE id = ? AND tenant_id = ?) AND tenant_id = ?", Timestamp.from(Instant.now()), id, tenantId, tenantId);
    }

    @Override
    @Transactional
    public void markFailed(String taskId, String tenantId, String diagnostic) {
        UUID id = UUID.fromString(taskId);
        jdbcTemplate.update("UPDATE ingestion_task SET state = 'FAILED', failure_diagnostic = ?, updated_at = ? WHERE id = ? AND tenant_id = ?", diagnostic, Timestamp.from(Instant.now()), id, tenantId);
        jdbcTemplate.update("UPDATE knowledge_version SET state = 'FAILED', failure_diagnostic = ?, updated_at = ? "
                + "WHERE id = (SELECT version_id FROM ingestion_task WHERE id = ? AND tenant_id = ?) AND tenant_id = ?", diagnostic, Timestamp.from(Instant.now()), id, tenantId, tenantId);
    }
}
