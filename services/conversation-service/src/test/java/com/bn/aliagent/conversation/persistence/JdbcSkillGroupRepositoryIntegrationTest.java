package com.bn.aliagent.conversation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcSkillGroupRepositoryIntegrationTest {
    @Test
    void 只为已关联的已验证标签返回启用技能组() throws Exception {
        String schema = "test_skill_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure().dataSource(url, "postgres", "123456").schemas(schema).locations("filesystem:src/main/resources/db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            UUID groupId = UUID.randomUUID();
            UUID tagId = UUID.randomUUID();
            jdbc.update("INSERT INTO agent_skill_group (id,tenant_id,name,routing_rule_version,enabled) VALUES (?, 'test-tenant','售后','p7-routing-v1',true)", groupId);
            jdbc.update("INSERT INTO agent_skill_tag (id,tenant_id,code) VALUES (?, 'test-tenant','AFTERSALE_VERIFIED')", tagId);
            jdbc.update("INSERT INTO agent_skill_group_tag (tenant_id,skill_group_id,tag_id) VALUES ('test-tenant',?,?)", groupId, tagId);

            assertEquals(groupId, new JdbcSkillGroupRepository(jdbc).enabledForTags("test-tenant", Set.of("AFTERSALE_VERIFIED")).get(0).id());
            assertEquals(0, new JdbcSkillGroupRepository(jdbc).enabledForTags("test-tenant", Set.of("FORGED")).size());
        } finally { execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
    private void execute(String sql) throws Exception { try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "postgres", "123456"); Statement statement = connection.createStatement()) { statement.execute(sql); } }
}
