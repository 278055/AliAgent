package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcDatasetWorkflowIntegrationTest {
    @Test
    void persistsReviewedCandidateAndImmutablePublishedSnapshotWithTenantIsolation() throws Exception {
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

            JdbcEvaluationDatasetRepository repository = new JdbcEvaluationDatasetRepository(jdbc,
                    new PublicDatasetAnonymizer(new DeterministicAnonymizer("test-p8")));
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
            String snapshot = jdbc.queryForObject("SELECT sample_json::text FROM evaluation_sample_snapshot", String.class);
            assertTrue(snapshot.contains("ORDER_QUERY"));
            assertTrue(snapshot.contains("ORDER"));

            UUID secondCandidate = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, ?, ?, ?::jsonb, ?, 'ACCEPTED', 'test-v1', ?)", secondCandidate, "test-p8-tenant-a", UUID.randomUUID(), "{\"input\":\"second\"}", "c".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));
            var concurrentDraft = datasets.createDraft("test-p8-tenant-a", "concurrent-dataset");
            var executor = Executors.newFixedThreadPool(2);
            try {
                var adds = executor.invokeAll(java.util.List.of((Callable<Void>) () -> { datasets.addCandidate("test-p8-tenant-a", concurrentDraft.id(), candidate); return null; }, (Callable<Void>) () -> { datasets.addCandidate("test-p8-tenant-a", concurrentDraft.id(), secondCandidate); return null; }));
                for (var add : adds) add.get();
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_draft_sample WHERE draft_id = ?", Integer.class, concurrentDraft.id()));
                var publishes = executor.invokeAll(java.util.List.of((Callable<Boolean>) () -> { try { datasets.publish("test-p8-tenant-a", concurrentDraft.id(), false, null); return true; } catch (IllegalStateException expected) { return false; } }, (Callable<Boolean>) () -> { try { datasets.publish("test-p8-tenant-a", concurrentDraft.id(), false, null); return true; } catch (IllegalStateException expected) { return false; } }));
                assertEquals(1, (publishes.get(0).get() ? 1 : 0) + (publishes.get(1).get() ? 1 : 0));
            } finally { executor.shutdownNow(); }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_version WHERE dataset_id = ?", Integer.class, concurrentDraft.id()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot WHERE dataset_version_id IN (SELECT id FROM evaluation_dataset_version WHERE dataset_id = ?)", Integer.class, concurrentDraft.id()));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void anonymizesPublicPublicationInsteadOfRejectingIt() {
        String schema = "test_p8_public_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema).locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID candidate = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at, review_expected, review_labels) VALUES (?, ?, ?, ?::jsonb, ?, 'ACCEPTED', 'test-v1', ?, '{}'::jsonb, '[]'::jsonb)", candidate, "test-p8-tenant-a", UUID.randomUUID(), "{\"phone\":\"13800138000\"}", "b".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));
            var anonymizer = new PublicDatasetAnonymizer(new DeterministicAnonymizer("test-p8"));
            var datasets = new EvaluationDatasetService(new JdbcEvaluationDatasetRepository(jdbc, anonymizer), anonymizer);
            var draft = datasets.createDraft("test-p8-tenant-a", "public-dataset");
            datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate);
            var published = datasets.publish("test-p8-tenant-a", draft.id(), true, com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization.active("admin", Instant.now()));
            assertTrue(published.publiclyShared());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot WHERE sample_json::text LIKE '%13800138000%'", Integer.class));
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
}
