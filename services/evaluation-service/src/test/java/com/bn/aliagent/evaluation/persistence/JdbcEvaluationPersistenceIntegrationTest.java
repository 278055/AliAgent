package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.evaluation.intake.EvaluationEventEnvelope;
import com.bn.aliagent.evaluation.intake.EventIntakeService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcEvaluationPersistenceIntegrationTest {
    @Test
    void persistsOneAnonymizedCandidateForDuplicateEventAndRejectsForeignTenant() {
        String schema = "test_p8_jdbc_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath()).load().migrate();
            EventIntakeService service = new EventIntakeService(new JdbcEventInbox(jdbc),
                    (payload, tenantId) -> new com.bn.aliagent.evaluation.intake.AnonymizedPayload(
                            com.bn.aliagent.evaluation.intake.IntakeAnonymizationStatus.SAFE, "{\"message\":\"[REDACTED]\"}", "anon-v1", "a".repeat(64)),
                    new JdbcCandidateSink(jdbc), Clock.systemUTC());
            UUID eventId = UUID.randomUUID();
            EvaluationEventEnvelope event = new EvaluationEventEnvelope(eventId, "conversation.feedback.received", 1, Instant.now(),
                    "test-p8-tenant-a", UUID.randomUUID().toString(), "test", Map.of("message", "13800138000"));

            service.accept(event, "test-p8-tenant-a");
            service.accept(event, "test-p8-tenant-a");

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_candidate WHERE tenant_id = 'test-p8-tenant-a'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_candidate WHERE anonymized_body::text LIKE '%13800138000%'", Integer.class));
            assertThrows(SecurityException.class, () -> new JdbcCandidateRepository(jdbc).require("test-p8-tenant-b",
                    jdbc.queryForObject("SELECT id FROM evaluation_candidate", UUID.class)));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
