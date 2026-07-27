package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.evaluation.anonymization.DeterministicAnonymizer;
import com.bn.aliagent.evaluation.anonymization.PublicDatasetAnonymizer;
import com.bn.aliagent.evaluation.candidate.CandidateReviewCommand;
import com.bn.aliagent.evaluation.candidate.CandidateReviewService;
import com.bn.aliagent.evaluation.candidate.ReviewAction;
import com.bn.aliagent.evaluation.dataset.EvaluationDatasetService;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcDatasetWorkflowIntegrationTest {
    @Test
    void persistsReviewedCandidateAndImmutablePublishedSnapshotWithTenantIsolation() {
        String schema = "test_p8_dataset_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID candidate = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, ?, ?, ?::jsonb, ?, 'PENDING_REVIEW', 'test-v1', ?)",
                    candidate, "test-p8-tenant-a", UUID.randomUUID(), "{\"input\":\"where is my order\"}", "a".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));

            JdbcEvaluationDatasetRepository repository = new JdbcEvaluationDatasetRepository(jdbc);
            CandidateReviewService reviews = new CandidateReviewService(repository);
            EvaluationDatasetService datasets = new EvaluationDatasetService(repository,
                    new PublicDatasetAnonymizer(new DeterministicAnonymizer("test-p8")));

            reviews.review(new CandidateReviewCommand(candidate, "test-p8-tenant-a", "admin", ReviewAction.ACCEPT,
                    Map.of("intent", "ORDER_QUERY"), Set.of("ORDER"), "test-p8-review"));
            var draft = datasets.createDraft("test-p8-tenant-a", "test-p8-dataset");
            datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate);
            var published = datasets.publish("test-p8-tenant-a", draft.id(), false, null);

            assertThrows(IllegalStateException.class, () -> datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate));
            assertThrows(SecurityException.class, () -> datasets.requirePublished("test-p8-tenant-b", published.id()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot", Integer.class));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
