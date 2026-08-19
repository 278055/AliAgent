package com.bn.aliagent.insight.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class P9PersistenceIntegrationTest {
    @Test
    void migratesEmptyInsightSchemaAndKeepsInboxIdempotencyTenantScoped() throws Exception {
        String schema = "test_p9_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        createSchema(schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:src/main/resources/db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            assertEquals(15, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name LIKE 'insight_%'", Integer.class));
            JdbcInsightEventInbox inbox = new JdbcInsightEventInbox(jdbc);
            UUID eventId = UUID.randomUUID();
            Instant now = Instant.now();
            InsightEventEnvelope first = event(eventId, "test-tenant-a");
            InsightEventEnvelope otherTenant = event(eventId, "test-tenant-b");

            assertEquals(1, inbox.reserve(first, "digest", now).stream().count());
            assertEquals(0, inbox.reserve(first, "digest", now).stream().count());
            assertEquals(1, inbox.reserve(otherTenant, "digest", now).stream().count());
            inbox.complete("test-tenant-a", "test-producer", eventId, now);
            assertEquals("COMPLETED", inbox.require("test-tenant-a", "test-producer", eventId).status().name());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM insight_event_inbox", Integer.class));
        } finally { dropSchema(schema); }
    }

    private static InsightEventEnvelope event(UUID eventId, String tenant) {
        return new InsightEventEnvelope(eventId, "order.paid", 1, Instant.now(), tenant, "trace", "test-producer", Map.of("evidenceRef", "test-evidence"));
    }
    private static void createSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "CREATE SCHEMA " + schema); }
    private static void dropSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    private static void execute(String url, String sql) throws Exception { try (Connection connection = DriverManager.getConnection(url, "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
