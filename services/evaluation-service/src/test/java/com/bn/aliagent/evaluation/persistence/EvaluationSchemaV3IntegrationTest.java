package com.bn.aliagent.evaluation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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

        flyway.migrate();
        assertEquals("11", flyway.info().current().getVersion().getVersion());
        List<String> tables = List.of(
                "evaluation_dataset_draft_sample",
                "evaluation_run_manifest",
                "evaluation_metric_evidence",
                "evaluation_dashscope_approval",
                "evaluation_budget_ledger",
                "evaluation_gate_proof",
                "evaluation_audit");
        for (String table : tables) {
            assertTrue(columnExists(table, "tenant_id"));
        }
        assertTrue(columnExists("evaluation_dataset_draft_sample", "dataset_id"));
        assertTrue(columnExists("evaluation_gate_proof", "canonical_payload"));
        assertRejectsCrossTenantDatasetReference();
        assertRejectsCrossTenantDatasetVersionReference();
        assertRejectsCrossTenantRunDatasetVersionReference();
    }

    @Test
    void upgradesAnAlreadyDeployedV3SchemaWithoutChecksumMismatch() throws SQLException {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }

        Flyway deployedV3 = Flyway.configure()
                .dataSource(databaseUrl(), databaseUser(), databasePassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("filesystem:src/main/resources/db/migration")
                .target("3")
                .load();
        deployedV3.migrate();

        Flyway latest = Flyway.configure()
                .dataSource(databaseUrl(), databaseUser(), databasePassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("filesystem:src/main/resources/db/migration")
                .load();
        latest.migrate();

        assertEquals("11", latest.info().current().getVersion().getVersion());
        var v6 = java.util.Arrays.stream(latest.info().all())
                .filter(migration -> migration.getVersion() != null && "6".equals(migration.getVersion().getVersion()))
                .findFirst()
                .orElseThrow();
        assertNotNull(v6.getState());
        assertEquals("Success", v6.getState().getDisplayName());
        assertEquals(128, varcharLength("evaluation_run_manifest", "tenant_id"));
        assertForeignKey("evaluation_run_manifest", "tenant_id", "run_id", "evaluation_run");
        assertForeignKey("evaluation_gate_proof", "tenant_id", "decision_id", "evaluation_gate_decision");
    }

    @Test
    void backfillsUniqueV8SnapshotCandidateBeforeAddingTenantForeignKey() throws SQLException {
        migrateTo("8");
        UUID dataset = UUID.randomUUID(), candidate = UUID.randomUUID(), version = UUID.randomUUID(), snapshot = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".evaluation_candidate (id,tenant_id,source_event_id,body_digest,status,anonymization_rule_version,expires_at) VALUES ('" + candidate + "','tenant-v9','" + UUID.randomUUID() + "','" + "a".repeat(64) + "','ACCEPTED','v1',CURRENT_TIMESTAMP)");
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset (id,tenant_id,name,state) VALUES ('" + dataset + "','tenant-v9','v9','PUBLISHED')");
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset_version (id,tenant_id,dataset_id,version_number,content_digest,visibility) VALUES ('" + version + "','tenant-v9','" + dataset + "',1,'" + "b".repeat(64) + "','PRIVATE')");
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset_draft_sample (tenant_id,draft_id,sample_id,dataset_id,sample_payload,sample_order) VALUES ('tenant-v9','" + dataset + "','" + candidate + "','" + dataset + "','{}',0)");
            statement.execute("INSERT INTO " + schema + ".evaluation_sample_snapshot (id,tenant_id,dataset_version_id,sample_json) VALUES ('" + snapshot + "','tenant-v9','" + version + "','{}')");
        }
        migrateLatest();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); var result = statement.executeQuery("SELECT candidate_id FROM " + schema + ".evaluation_sample_snapshot WHERE id='" + snapshot + "'")) { assertTrue(result.next()); assertEquals(candidate, result.getObject(1, UUID.class)); }
    }

    @Test
    void rejectsAmbiguousV8SnapshotCandidateBackfill() throws SQLException {
        migrateTo("8");
        UUID dataset = UUID.randomUUID(), version = UUID.randomUUID(), snapshot = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset (id,tenant_id,name,state) VALUES ('" + dataset + "','tenant-amb','amb','PUBLISHED')");
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset_version (id,tenant_id,dataset_id,version_number,content_digest,visibility) VALUES ('" + version + "','tenant-amb','" + dataset + "',1,'" + "c".repeat(64) + "','PRIVATE')");
            for (int i = 0; i < 2; i++) { UUID candidate = UUID.randomUUID(); statement.execute("INSERT INTO " + schema + ".evaluation_candidate (id,tenant_id,source_event_id,body_digest,status,anonymization_rule_version,expires_at) VALUES ('" + candidate + "','tenant-amb','" + UUID.randomUUID() + "','" + "d".repeat(64) + "','ACCEPTED','v1',CURRENT_TIMESTAMP)"); statement.execute("INSERT INTO " + schema + ".evaluation_dataset_draft_sample (tenant_id,draft_id,sample_id,dataset_id,sample_payload,sample_order) VALUES ('tenant-amb','" + dataset + "','" + candidate + "','" + dataset + "','{}'," + i + ")"); }
            statement.execute("INSERT INTO " + schema + ".evaluation_sample_snapshot (id,tenant_id,dataset_version_id,sample_json) VALUES ('" + snapshot + "','tenant-amb','" + version + "','{}')");
        }
        var exception = assertThrows(RuntimeException.class, this::migrateLatest);
        assertTrue(exception.getMessage().contains("cannot uniquely backfill"));
    }

    private void migrateTo(String target) { Flyway.configure().dataSource(databaseUrl(), databaseUser(), databasePassword()).schemas(schema).defaultSchema(schema).createSchemas(true).locations("filesystem:src/main/resources/db/migration").target(target).load().migrate(); }
    private void migrateLatest() { Flyway.configure().dataSource(databaseUrl(), databaseUser(), databasePassword()).schemas(schema).defaultSchema(schema).createSchemas(true).locations("filesystem:src/main/resources/db/migration").load().migrate(); }

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

    private void assertRejectsCrossTenantDatasetReference() throws SQLException {
        UUID datasetId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset "
                    + "(id, tenant_id, name, state) VALUES ('" + datasetId + "', 'tenant-a', 'dataset', 'DRAFT')");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schema
                    + ".evaluation_dataset_draft_sample (tenant_id, draft_id, sample_id, dataset_id, sample_payload, sample_order) "
                    + "VALUES ('tenant-b', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + datasetId
                    + "', '{}'::jsonb, 1)"));
        }
    }

    private int varcharLength(String tableName, String columnName) throws SQLException {
        String sql = "SELECT character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (Connection connection = openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, tableName);
            statement.setString(3, columnName);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private void assertForeignKey(String tableName, String firstColumn, String secondColumn, String referencedTable)
            throws SQLException {
        String sql = "SELECT 1 FROM information_schema.table_constraints table_constraint "
                + "JOIN information_schema.key_column_usage first_key "
                + "ON table_constraint.constraint_schema = first_key.constraint_schema "
                + "AND table_constraint.constraint_name = first_key.constraint_name "
                + "JOIN information_schema.key_column_usage second_key "
                + "ON table_constraint.constraint_schema = second_key.constraint_schema "
                + "AND table_constraint.constraint_name = second_key.constraint_name "
                + "JOIN information_schema.constraint_column_usage referenced "
                + "ON table_constraint.constraint_schema = referenced.constraint_schema "
                + "AND table_constraint.constraint_name = referenced.constraint_name "
                + "WHERE table_constraint.constraint_schema = ? AND table_constraint.table_name = ? "
                + "AND table_constraint.constraint_type = 'FOREIGN KEY' "
                + "AND first_key.column_name = ? AND first_key.ordinal_position = 1 "
                + "AND second_key.column_name = ? AND second_key.ordinal_position = 2 "
                + "AND referenced.table_name = ?";
        try (Connection connection = openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, tableName);
            statement.setString(3, firstColumn);
            statement.setString(4, secondColumn);
            statement.setString(5, referencedTable);
            try (var resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
            }
        }
    }

    private void assertRejectsCrossTenantDatasetVersionReference() throws SQLException {
        UUID datasetId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset "
                    + "(id, tenant_id, name, state) VALUES ('" + datasetId + "', 'tenant-a-v2', 'dataset-v2', 'DRAFT')");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schema
                    + ".evaluation_dataset_version (id, tenant_id, dataset_id, version_number, content_digest, visibility) "
                    + "VALUES ('" + UUID.randomUUID() + "', 'tenant-b-v2', '" + datasetId
                    + "', 1, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'PRIVATE')"));
        }
    }

    private void assertRejectsCrossTenantRunDatasetVersionReference() throws SQLException {
        UUID datasetId = UUID.randomUUID();
        UUID datasetVersionId = UUID.randomUUID();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset "
                    + "(id, tenant_id, name, state) VALUES ('" + datasetId + "', 'tenant-a-run', 'dataset-run', 'DRAFT')");
            statement.execute("INSERT INTO " + schema + ".evaluation_dataset_version "
                    + "(id, tenant_id, dataset_id, version_number, content_digest, visibility) VALUES ('"
                    + datasetVersionId + "', 'tenant-a-run', '" + datasetId
                    + "', 1, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'PRIVATE')");
            assertThrows(SQLException.class, () -> statement.execute("INSERT INTO " + schema
                    + ".evaluation_run (id, tenant_id, dataset_version_id, manifest_digest, mode, status) VALUES ('"
                    + UUID.randomUUID() + "', 'tenant-b-run', '" + datasetVersionId
                    + "', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'FULL', 'PENDING')"));
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
