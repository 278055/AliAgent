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
import java.util.List;
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
    void rejectsDirectUpdateAndDeleteOfPublishedSnapshot() {
        String schema = "test_p8_snapshot_immutable_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID dataset = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            UUID snapshot = UUID.randomUUID();
            UUID candidate = insertAcceptedCandidate(jdbc);
            jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, 'test-tenant', 'immutable', 'PUBLISHED')", dataset);
            jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, 'test-tenant', ?, 1, ?, 'PRIVATE')", version, dataset, "a".repeat(64));
            jdbc.update("INSERT INTO evaluation_sample_snapshot (id, tenant_id, dataset_version_id, sample_json, candidate_id, snapshot_payload) VALUES (?, 'test-tenant', ?, '{\"input\":\"test\",\"expected\":{}}'::jsonb, ?, '{}'::jsonb)", snapshot, version, candidate);

            assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE evaluation_sample_snapshot SET sample_json = '{\"input\":\"changed\"}'::jsonb WHERE id = ?", snapshot));
            assertThrows(RuntimeException.class, () -> jdbc.update("DELETE FROM evaluation_sample_snapshot WHERE id = ?", snapshot));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void rejectsCoercedSnapshotConstraintTypes() {
        String schema = "test_p8_snapshot_types_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID dataset = UUID.randomUUID();
            UUID version = UUID.randomUUID();
            UUID candidate = insertAcceptedCandidate(jdbc);
            jdbc.update("INSERT INTO evaluation_dataset (id, tenant_id, name, state) VALUES (?, 'test-tenant', 'typed', 'PUBLISHED')", dataset);
            jdbc.update("INSERT INTO evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES (?, 'test-tenant', ?, 1, ?, 'PRIVATE')", version, dataset, "b".repeat(64));
            jdbc.update("INSERT INTO evaluation_sample_snapshot (id, tenant_id, dataset_version_id, sample_json, candidate_id, snapshot_payload) VALUES (?, 'test-tenant', ?, '{\"input\":\"test\",\"expected\":{\"intent\":\"GENERAL\"}}'::jsonb, ?, '{\"allowedTools\":[1,true],\"prohibitedTools\":[],\"parameterConstraints\":{},\"citationRequirements\":[],\"factAssertions\":[],\"safetyLabels\":[],\"expectedHumanHandoff\":false,\"weights\":{\"quality\":\"1.0\"},\"applicableMetrics\":[]}'::jsonb)", UUID.randomUUID(), version, candidate);

            assertThrows(IllegalStateException.class, () -> new JdbcDatasetSnapshotPort(jdbc).published("test-tenant", version));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private UUID insertAcceptedCandidate(JdbcTemplate jdbc) {
        UUID candidate = UUID.randomUUID();
        jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, 'test-tenant', ?, '{}'::jsonb, ?, 'ACCEPTED', 'test-v1', ?)",
                candidate, UUID.randomUUID(), "f".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));
        return candidate;
    }

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

            Map<String, Object> expected = Map.of("intent", "ORDER_QUERY", "constraints", Map.of("region", "CN", "steps", List.of("lookup", "reply")));
            Set<String> labels = Set.of("ORDER", "PRIORITY");
            reviews.review(new CandidateReviewCommand(candidate, "test-p8-tenant-a", "admin", ReviewAction.ACCEPT, expected, labels, "test-p8-review"));
            var draft = datasets.createDraft("test-p8-tenant-a", "test-p8-dataset");
            datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate);
            var published = datasets.publish("test-p8-tenant-a", draft.id(), false, null);

            assertThrows(IllegalStateException.class, () -> datasets.addCandidate("test-p8-tenant-a", draft.id(), candidate));
            assertThrows(SecurityException.class, () -> datasets.requirePublished("test-p8-tenant-b", published.id()));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot", Integer.class));
            String snapshot = jdbc.queryForObject("SELECT sample_json::text FROM evaluation_sample_snapshot", String.class);
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshot);
            assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(expected), json.path("expected"));
            assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(labels), json.path("labels"));
            assertEquals(expected, repository.require("test-p8-tenant-a", candidate).expected());
            assertEquals(labels, repository.require("test-p8-tenant-a", candidate).labels());
            var reloaded = new JdbcEvaluationDatasetRepository(jdbc, new PublicDatasetAnonymizer(new DeterministicAnonymizer("test-p8"))).requirePublished("test-p8-tenant-a", published.id());
            assertEquals(1, reloaded.samples().size());
            assertEquals(expected, reloaded.samples().get(0).expected());
            assertEquals(labels, reloaded.samples().get(0).labels());
            assertEquals(candidate, reloaded.samples().get(0).candidateId());
            assertEquals(published.samples().get(0), reloaded.samples().get(0));
            var audit = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jdbc.queryForObject("SELECT event_payload::text FROM evaluation_audit", String.class));
            assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(expected), audit.path("expected"));
            assertTrue(audit.path("labels").isArray());

            UUID secondCandidate = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, ?, ?, ?::jsonb, ?, 'ACCEPTED', 'test-v1', ?)", secondCandidate, "test-p8-tenant-a", UUID.randomUUID(), "{\"input\":\"second\"}", "c".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));
            var concurrentDraft = datasets.createDraft("test-p8-tenant-a", "concurrent-dataset");
            var executor = Executors.newFixedThreadPool(2);
            try {
                var adds = executor.invokeAll(java.util.List.of((Callable<Void>) () -> { datasets.addCandidate("test-p8-tenant-a", concurrentDraft.id(), candidate); return null; }, (Callable<Void>) () -> { datasets.addCandidate("test-p8-tenant-a", concurrentDraft.id(), secondCandidate); return null; }));
                for (var add : adds) add.get();
                assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_draft_sample WHERE draft_id = ?", Integer.class, concurrentDraft.id()));
                var publishes = executor.invokeAll(java.util.List.of((Callable<Boolean>) () -> { try { datasets.publish("test-p8-tenant-a", concurrentDraft.id(), false, null); return true; } catch (IllegalStateException ignored) { return false; } }, (Callable<Boolean>) () -> { try { datasets.publish("test-p8-tenant-a", concurrentDraft.id(), false, null); return true; } catch (IllegalStateException ignored) { return false; } }));
                assertEquals(1, (publishes.get(0).get() ? 1 : 0) + (publishes.get(1).get() ? 1 : 0));
            } finally { executor.shutdownNow(); }
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_dataset_version WHERE dataset_id = ?", Integer.class, concurrentDraft.id()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_sample_snapshot WHERE dataset_version_id IN (SELECT id FROM evaluation_dataset_version WHERE dataset_id = ?)", Integer.class, concurrentDraft.id()));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void rollsBackCandidateReviewWhenAuditWriteFails() {
        String schema = "test_p8_review_tx_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema).locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            UUID candidate = UUID.randomUUID();
            jdbc.update("INSERT INTO evaluation_candidate (id, tenant_id, source_event_id, anonymized_body, body_digest, status, anonymization_rule_version, expires_at) VALUES (?, 'test-p8-tenant-a', ?, '{}'::jsonb, ?, 'PENDING_REVIEW', 'v1', ?)", candidate, UUID.randomUUID(), "d".repeat(64), Timestamp.from(Instant.now().plusSeconds(3600)));
            jdbc.execute("CREATE FUNCTION fail_review_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'audit failure'; END $$");
            jdbc.execute("CREATE TRIGGER fail_review_audit BEFORE INSERT ON evaluation_audit FOR EACH ROW EXECUTE FUNCTION fail_review_audit()");
            var reviews = new CandidateReviewService(new JdbcEvaluationDatasetRepository(jdbc, new PublicDatasetAnonymizer(new DeterministicAnonymizer("test-p8"))));
            assertThrows(RuntimeException.class, () -> reviews.review(new CandidateReviewCommand(candidate, "test-p8-tenant-a", "admin", ReviewAction.ACCEPT, Map.of("intent", "x"), Set.of("L"), "x")));
            assertEquals("PENDING_REVIEW", jdbc.queryForObject("SELECT status FROM evaluation_candidate WHERE id = ?", String.class, candidate));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_candidate_review WHERE candidate_id = ?", Integer.class, candidate));
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
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
            var forbiddenDraft = datasets.createDraft("test-p8-tenant-a", "forbidden-public-dataset");
            datasets.addCandidate("test-p8-tenant-a", forbiddenDraft.id(), candidate);
            assertThrows(SecurityException.class, () -> datasets.publish("test-p8-tenant-a", forbiddenDraft.id(), true, null));
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
}
