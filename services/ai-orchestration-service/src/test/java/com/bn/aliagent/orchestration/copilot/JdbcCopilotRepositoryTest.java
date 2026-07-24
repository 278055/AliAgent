package com.bn.aliagent.orchestration.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCopilotRepositoryTest {
    @Test
    void persistsSuggestionAndDeduplicatesInboxAndRequest() throws Exception {
        String schema = "test_p7_copilot_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        createSchema(schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema).locations("filesystem:src/main/resources/db/migration").load().migrate();
            var repository = new JdbcCopilotRepository(new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456")));
            UUID requestId = UUID.randomUUID();
            var suggestion = new CopilotModels.Suggestion(UUID.randomUUID(), "test-tenant", UUID.randomUUID(), UUID.randomUUID(), "staff-1", 0,
                    CopilotModels.Visibility.PRIVATE, CopilotModels.SuggestionStatus.GENERATED, "建议", "", "", "model-v1", "prompt-v1", "workflow-v1", List.of(), java.time.Instant.now());

            assertTrue(repository.claimInbox(UUID.randomUUID()));
            repository.save(suggestion, requestId);

            assertEquals(suggestion.suggestionId(), repository.findByRequestId(requestId).orElseThrow().suggestionId());
            assertEquals(suggestion.suggestionId(), repository.findSuggestion(suggestion.suggestionId()).orElseThrow().suggestionId());
        } finally { dropSchema(schema); }
    }
    private void createSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "CREATE SCHEMA " + schema); }
    private void dropSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    private void execute(String url, String sql) throws Exception { try (Connection connection = DriverManager.getConnection(url, "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
