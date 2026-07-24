package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.JdbcConversationRepository;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RealtimeDatabaseIntegrationTest {
    @Test
    void persistsIdempotentHumanMessagePresenceAndConnectionInIsolatedSchema() throws Exception {
        String schema = "test_p4_c_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        createSchema(schema);
        try {
            execute(url, migration("V2__conversation_core.sql"));
            execute(url, migration("V4__realtime_collaboration.sql"));
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            UUID conversationId = UUID.randomUUID();
            jdbc.update("INSERT INTO conversation (id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at) VALUES (?, 'test-p4-c-tenant', 'member-1', 'test-p4-c', 'AI_ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", conversationId);
            ConversationService conversations = new ConversationService(new JdbcConversationRepository(jdbc));
            TrustedConversationRequestContext staff = new TrustedConversationRequestContext("test-p4-c-tenant", "staff-1", "STAFF", "trace", UUID.randomUUID());
            UUID clientMessageId = UUID.randomUUID();

            var first = conversations.submitStaffMessage(staff, conversationId, "test-p4-c-message", clientMessageId);
            var replay = conversations.submitStaffMessage(staff, conversationId, "test-p4-c-message", clientMessageId);
            JdbcRealtimeStateRepository states = new JdbcRealtimeStateRepository(jdbc, 30);
            RealtimeConnection connection = new RealtimeConnection("test-p4-c-tenant", conversationId, UUID.randomUUID(), "instance-b");
            states.saveConnection(connection);
            states.savePresence("test-p4-c-tenant", "staff-1", "ONLINE");
            states.saveHumanState("test-p4-c-tenant", conversationId, "staff-1", "HUMAN_ACTIVE");

            assertEquals(first.id(), replay.id());
            assertEquals(1L, first.sequence());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM message", Integer.class));
            assertEquals("ONLINE", jdbc.queryForObject("SELECT status FROM conversation_agent_presence", String.class));
            assertEquals("HUMAN_ACTIVE", jdbc.queryForObject("SELECT status FROM conversation_human_state", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM conversation_realtime_connection WHERE expires_at > CURRENT_TIMESTAMP", Integer.class));
            assertThrows(ConversationException.class, () -> conversations.takeOver(new TrustedConversationRequestContext("other-tenant", "staff-1", "STAFF", "trace", UUID.randomUUID()), conversationId));
        } finally { dropSchema(schema); }
    }

    private String migration(String name) throws Exception { return Files.readString(Path.of("src/main/resources/db/migration", name)); }
    private void createSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "CREATE SCHEMA " + schema); }
    private void dropSchema(String schema) throws Exception { execute("jdbc:postgresql://localhost:5432/postgres", "DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    private void execute(String url, String sql) throws Exception { try (Connection connection = DriverManager.getConnection(url, "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
