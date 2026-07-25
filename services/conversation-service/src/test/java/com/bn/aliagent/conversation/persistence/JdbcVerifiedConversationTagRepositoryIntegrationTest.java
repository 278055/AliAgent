package com.bn.aliagent.conversation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcVerifiedConversationTagRepositoryIntegrationTest {
    @Test
    void 标签由受信任来源幂等保存并按租户会话读取() throws Exception {
        String schema = "test_tag_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema).locations("filesystem:src/main/resources/db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            UUID conversation = UUID.randomUUID();
            jdbc.update("INSERT INTO conversation (id,tenant_id,owner_subject_id,title,status,pinned,created_at,updated_at) VALUES (?, 'tenant-a','member','test','WAITING_HUMAN',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", conversation);
            JdbcVerifiedConversationTagRepository tags = new JdbcVerifiedConversationTagRepository(jdbc);
            tags.verify("tenant-a", conversation, "AFTERSALE_VERIFIED", "AFTERSALE_VERIFIED", Instant.now());
            tags.verify("tenant-a", conversation, "AFTERSALE_VERIFIED", "AFTERSALE_VERIFIED", Instant.now());

            assertEquals(Set.of("AFTERSALE_VERIFIED"), tags.verifiedTags("tenant-a", conversation));
            assertEquals(Set.of(), tags.verifiedTags("tenant-b", conversation));
        } finally { execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
    private void execute(String sql) throws Exception { try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
