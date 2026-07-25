package com.bn.aliagent.knowledge.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅接受已验证服务身份的内部调用；快照主体来自 Gateway 注入的可信头。 */
@RestController
@Profile("database")
@RequestMapping("/internal/api/v1/authorization-snapshots")
public class AuthorizationSnapshotController {
    private final JdbcTemplate jdbc;
    public AuthorizationSnapshotController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public Map<String, Object> issue(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE))) {
            throw new IllegalArgumentException("服务认证未完成");
        }
        String tenantId = required(request, "X-Tenant-Id");
        String subjectId = required(request, "X-Subject-Id");
        String subjectType = required(request, "X-Subject-Type");
        if (!"MEMBER".equals(subjectType) && !"STAFF".equals(subjectType)) throw new IllegalArgumentException("subject type is invalid");
        String roles = required(request, "X-User-Roles");
        String permissions = required(request, "X-User-Permissions");
        Instant now = Instant.now(); UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO knowledge_authorization_snapshot (id,tenant_id,subject_id,subject_type,roles_csv,permissions_csv,issued_at,expires_at) VALUES (?,?,?,?,?,?,?,?)", id, tenantId, subjectId, subjectType, roles, permissions, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(300)));
        if (containsPermission(permissions, "KNOWLEDGE_READ")) {
            jdbc.update("INSERT INTO knowledge_authorization_snapshot_domain (snapshot_id,tenant_id,domain_id,permission) "
                    + "SELECT ?,tenant_id,id,'KNOWLEDGE_READ' FROM knowledge_domain WHERE tenant_id=?", id, tenantId);
        }
        return Map.of("id", id, "expiresAt", now.plusSeconds(300));
    }
    private boolean containsPermission(String permissions, String permission) { return java.util.Arrays.stream(permissions.split(",")).map(String::trim).anyMatch(permission::equals); }
    private String required(HttpServletRequest request, String name) { String value=request.getHeader(name); if(value==null||value.isBlank()) throw new IllegalArgumentException(name); return value; }
}
