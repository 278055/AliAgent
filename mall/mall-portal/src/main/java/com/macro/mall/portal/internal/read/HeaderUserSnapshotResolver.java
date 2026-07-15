package com.macro.mall.portal.internal.read;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class HeaderUserSnapshotResolver {
    public UserSnapshot resolve(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        String subjectId = request.getHeader("X-Subject-Id");
        String subjectType = request.getHeader("X-Subject-Type");
        if (isBlank(tenantId) || isBlank(subjectId) || isBlank(subjectType)) {
            throw new InternalAuthenticationException("missing user snapshot");
        }
        try {
            return new UserSnapshot(tenantId, Long.valueOf(subjectId), SubjectType.valueOf(subjectType), roles(request));
        } catch (IllegalArgumentException exception) {
            throw new InternalAuthenticationException("invalid user snapshot");
        }
    }

    private Set<String> roles(HttpServletRequest request) {
        String roles = request.getHeader("X-User-Roles");
        if (isBlank(roles)) {
            return Collections.emptySet();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
