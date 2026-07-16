package com.bn.aliagent.knowledge.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public record TrustedKnowledgeRequestContext(String tenantId, String subjectId, String subjectType, String roles,
        String permissions, String traceId, UUID authorizationSnapshotId) {
    public static final String ATTRIBUTE = TrustedKnowledgeRequestContext.class.getName();

    public static TrustedKnowledgeRequestContext from(HttpServletRequest request) {
        String tenantId = required(request, "X-Tenant-Id");
        String subjectId = required(request, "X-Subject-Id");
        String subjectType = required(request, "X-Subject-Type");
        String roles = required(request, "X-User-Roles");
        String permissions = required(request, "X-User-Permissions");
        return new TrustedKnowledgeRequestContext(tenantId, subjectId, subjectType, roles, permissions,
                required(request, "X-Trace-Id"),
                UUID.fromString(required(request, "X-Authorization-Snapshot-Id")));
    }

    public static void bind(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, from(request));
    }

    public static TrustedKnowledgeRequestContext require(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (!(value instanceof TrustedKnowledgeRequestContext context)) {
            throw new IllegalStateException("可信知识请求上下文未绑定");
        }
        return context;
    }

    private static String required(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少可信上下文: " + header);
        return value;
    }
}
