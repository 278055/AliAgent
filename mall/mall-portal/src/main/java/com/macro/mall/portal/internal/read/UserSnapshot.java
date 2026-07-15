package com.macro.mall.portal.internal.read;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UserSnapshot {
    public static final String REQUEST_ATTRIBUTE = UserSnapshot.class.getName();

    private final String tenantId;
    private final Long subjectId;
    private final SubjectType subjectType;
    private final Set<String> roles;

    public UserSnapshot(String tenantId, Long subjectId, SubjectType subjectType, Set<String> roles) {
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.subjectType = subjectType;
        this.roles = Collections.unmodifiableSet(new HashSet<>(roles));
    }

    public static UserSnapshot member(Long subjectId) {
        return new UserSnapshot("test-tenant", subjectId, SubjectType.MEMBER, Collections.emptySet());
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
