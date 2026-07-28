package com.bn.aliagent.evaluation.persistence;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import com.bn.aliagent.evaluation.runner.EvaluationRunRepository;
import com.bn.aliagent.evaluation.runner.ReplayResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcEvaluationRunRepository implements EvaluationRunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final TransactionTemplate transactions;
    public JdbcEvaluationRunRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())); }
    @Override public UUID create(String tenantId, EvaluationManifest manifest, String mode) {
        UUID runId = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_run (id, tenant_id, dataset_version_id, manifest_digest, mode, status) VALUES (?, ?, ?, ?, ?, 'RUNNING')", runId, tenantId, manifest.datasetVersionId(), manifest.digest(), mode);
        jdbc.update("INSERT INTO evaluation_run_manifest (tenant_id, run_id, draft_id, manifest_payload, model_name, model_version, prompt_version, created_by, dataset_version_id) VALUES (?, ?, NULL, CAST(? AS jsonb), ?, ?, ?, 'evaluation-service', ?)", tenantId, runId, write(manifest), manifest.modelVersionId().toString(), manifest.modelVersionId().toString(), manifest.promptVersionId().toString(), manifest.datasetVersionId());
        return runId;
    }
    @Override public void storeResult(String tenantId, UUID runId, ReplayFixture fixture, ReplayResult result) {
        transactions.executeWithoutResult(status -> {
            String state = jdbc.query("SELECT status FROM evaluation_run WHERE tenant_id = ? AND id = ? FOR UPDATE", rs -> rs.next() ? rs.getString(1) : null, tenantId, runId);
            if (!"RUNNING".equals(state)) throw new IllegalStateException("evaluation run is not running");
            jdbc.update("INSERT INTO evaluation_result (id, tenant_id, run_id, sample_id, evidence_json) VALUES (?, ?, ?, ?, CAST(? AS jsonb))", UUID.randomUUID(), tenantId, runId, fixture.sampleId(), write(result));
            jdbc.update("INSERT INTO evaluation_metric_evidence (tenant_id, evidence_id, run_id, metric_name, metric_value, evidence_payload) VALUES (?, ?, ?, 'replay', 1, CAST(? AS jsonb))", tenantId, UUID.randomUUID(), runId,
                    write(Map.of("sampleId", fixture.sampleId(), "fixture", fixture, "result", result)));
        });
    }
    @Override public void complete(String tenantId, UUID runId) {
        if (jdbc.update("UPDATE evaluation_run SET status = 'COMPLETED' WHERE tenant_id = ? AND id = ? AND status = 'RUNNING'", tenantId, runId) != 1) throw new IllegalStateException("evaluation run cannot complete");
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalStateException("evaluation JSON serialization failed", exception); } }
}
