package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EvaluationSchemaV3IntegrationTest {

    private final String schema = "test_p8_v3_" + UUID.randomUUID().toString().replace('-', '_');

    @Test
    void migratesV3WithTenantBoundDraftRunAndProofTables() throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(databaseUrl(), databaseUser(), databasePassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("filesystem:src/main/resources/db/migration")
                .load();

        assertEquals(3, flyway.migrate().migrationsExecuted);
        assertTrue(columnExists("evaluation_dataset_draft_sample", "tenant_id"));
        assertTrue(columnExists("evaluation_gate_proof", "canonical_payload"));
    }

    @AfterEach
    void dropSchema() throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (Connection connection = openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl(), databaseUser(), databasePassword());
    }

    private String databaseUrl() {
        return System.getenv().getOrDefault("TEST_DATABASE_URL", "jdbc:postgresql://localhost:5432/postgres");
    }

    private String databaseUser() {
        return System.getenv().getOrDefault("TEST_DATABASE_USER", "postgres");
    }

    private String databasePassword() {
        return System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "123456");
    }
}
