package com.macro.mall.security.identity;

import io.jsonwebtoken.Claims;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 已校验 JWT 形成的只读请求身份上下文，禁止由客户端参数构造。
 */
public final class IdentityContext {
    private final String subjectId;
    private final SubjectType subjectType;
    private final String tenantId;
    private final List<String> roles;
    private final List<String> permissions;

    private IdentityContext(String subjectId, SubjectType subjectType, String tenantId,
                            List<String> roles, List<String> permissions) {
        this.subjectId = subjectId;
        this.subjectType = subjectType;
        this.tenantId = tenantId;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    public static IdentityContext from(Claims claims) {
        return new IdentityContext(claims.getSubject(), SubjectType.valueOf(claims.get("subjectType", String.class)),
                claims.get("tenantId", String.class), claims.get("roles", List.class),
                claims.get("permissions", List.class));
    }

    public String getSubjectId() {
        return subjectId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
