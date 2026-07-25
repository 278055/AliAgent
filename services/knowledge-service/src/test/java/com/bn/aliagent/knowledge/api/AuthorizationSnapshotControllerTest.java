package com.bn.aliagent.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthorizationSnapshotControllerTest {
    @Test
    void 拒绝未通过服务认证的内部快照签发() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "test-tenant");
        request.addHeader("X-Subject-Id", "staff-1");
        request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-User-Roles", "STAFF");
        request.addHeader("X-User-Permissions", "KNOWLEDGE_READ");

        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationSnapshotController(new JdbcTemplate()).issue(request));
    }

    @Test
    void persistsSnapshotFromTrustedGatewayHeaders() throws Exception {
        String schema = "test_snapshot_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://localhost:5432/postgres?currentSchema=" + schema;
        execute("CREATE SCHEMA " + schema);
        try {
            execute("CREATE TABLE " + schema + ".knowledge_authorization_snapshot (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, subject_id VARCHAR(128) NOT NULL, subject_type VARCHAR(32) NOT NULL, roles_csv TEXT NOT NULL, permissions_csv TEXT NOT NULL, issued_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL)");
            execute("CREATE TABLE " + schema + ".knowledge_domain (id UUID PRIMARY KEY, tenant_id VARCHAR(128) NOT NULL, code VARCHAR(128) NOT NULL, name VARCHAR(255) NOT NULL)");
            execute("CREATE TABLE " + schema + ".knowledge_authorization_snapshot_domain (snapshot_id UUID NOT NULL, tenant_id VARCHAR(128) NOT NULL, domain_id UUID NOT NULL, permission VARCHAR(128) NOT NULL)");
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "postgres", "123456"));
            jdbc.update("INSERT INTO knowledge_domain (id,tenant_id,code,name) VALUES (?, 'test-tenant','after-sale','售后')", UUID.randomUUID());
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE, Boolean.TRUE);
            request.addHeader("X-Tenant-Id", "test-tenant"); request.addHeader("X-Subject-Id", "staff-1"); request.addHeader("X-Subject-Type", "STAFF");
            request.addHeader("X-User-Roles", "STAFF"); request.addHeader("X-User-Permissions", "KNOWLEDGE_READ"); request.addHeader("X-Trace-Id", "trace"); request.addHeader("X-Authorization-Snapshot-Id", UUID.randomUUID().toString());
            UUID id = (UUID) new AuthorizationSnapshotController(jdbc).issue(request).get("id");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_authorization_snapshot WHERE id=? AND tenant_id='test-tenant' AND subject_id='staff-1'", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_authorization_snapshot_domain WHERE snapshot_id=? AND permission='KNOWLEDGE_READ'", Integer.class, id));
        } finally { execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
    private void execute(String sql) throws Exception { try (Connection connection=DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres","postgres","123456"); Statement statement=connection.createStatement()) { statement.execute(sql); } }
}
