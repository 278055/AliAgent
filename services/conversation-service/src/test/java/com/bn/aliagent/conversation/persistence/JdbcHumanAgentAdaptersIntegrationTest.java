package com.bn.aliagent.conversation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.agent.AgentModels;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcHumanAgentAdaptersIntegrationTest {
    @Test
    void persistsQueueIdempotentlyAndLoadsOnlyOnlineAgentsWithCapacity() throws Exception {
        String schema = "test_p7_jdbc_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        createSchema(schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema)
                    .locations("filesystem:src/main/resources/db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            UUID conversationId = UUID.randomUUID();
            UUID groupId = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            jdbc.update("INSERT INTO conversation (id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at) VALUES (?, 'test-tenant', 'member-1', 'test-p7', 'WAITING_HUMAN', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", conversationId);
            jdbc.update("INSERT INTO agent_skill_group (id, tenant_id, name, routing_rule_version) VALUES (?, 'test-tenant', 'test-group', 'p7-routing-v1')", groupId);
            jdbc.update("INSERT INTO agent_skill_group_member (tenant_id, skill_group_id, staff_id, max_concurrent, enabled) VALUES ('test-tenant', ?, 'staff-online', 2, true), ('test-tenant', ?, 'staff-offline', 2, true)", groupId, groupId);
            jdbc.update("INSERT INTO conversation_agent_presence (tenant_id, staff_id, status) VALUES ('test-tenant', 'staff-online', 'ONLINE'), ('test-tenant', 'staff-offline', 'OFFLINE')");
            JdbcHumanAgentAdapters adapters = new JdbcHumanAgentAdapters(jdbc);
            HumanQueueModels.QueueItem item = new HumanQueueModels.QueueItem(UUID.randomUUID(), "test-tenant", conversationId, groupId, 100, "p7-routing-v1", "p7-priority-v1", java.time.Instant.now(), requestId, 0, HumanQueueModels.QueueStatus.WAITING);

            assertEquals(item.id(), adapters.createIfAbsent(item).id());
            assertEquals(item.id(), adapters.createIfAbsent(item).id());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM human_queue_item", Integer.class));
            assertEquals(1, adapters.candidates("test-tenant", groupId).size());
            assertEquals(AgentModels.Presence.ONLINE, adapters.candidates("test-tenant", groupId).get(0).presence());
        } finally {
            dropSchema(schema);
        }
    }

    private void createSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "CREATE SCHEMA " + schema); }
    private void dropSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    private void execute(String url, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
}
