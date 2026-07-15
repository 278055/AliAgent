package com.macro.mall.security.identity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * mall 对外 JWT 的稳定身份声明模型。
 */
public final class IdentityTokenClaims {
    private final String subjectId;
    private final String loginName;
    private final SubjectType subjectType;
    private final String tenantId;
    private final List<String> roles;
    private final List<String> permissions;

    private IdentityTokenClaims(String subjectId, String loginName, SubjectType subjectType, String tenantId,
                                List<String> roles, List<String> permissions) {
        this.subjectId = subjectId;
        this.loginName = loginName;
        this.subjectType = subjectType;
        this.tenantId = tenantId;
        this.roles = Collections.unmodifiableList(new ArrayList<>(roles));
        this.permissions = Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    public static IdentityTokenClaims of(String subjectId, String loginName, SubjectType subjectType, String tenantId,
                                         List<String> roles, List<String> permissions) {
        if (isBlank(subjectId) || isBlank(loginName) || subjectType == null || isBlank(tenantId)) {
            throw new IllegalArgumentException("Identity claims must be complete");
        }
        return new IdentityTokenClaims(subjectId, loginName, subjectType, tenantId,
                roles == null ? Collections.emptyList() : roles,
                permissions == null ? Collections.emptyList() : permissions);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getLoginName() {
        return loginName;
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
