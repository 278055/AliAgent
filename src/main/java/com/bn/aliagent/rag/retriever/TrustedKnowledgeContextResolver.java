package com.bn.aliagent.rag.retriever;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前请求中读取完整的可信上下文；任何字段缺失均拒绝远程调用。 */
public class TrustedKnowledgeContextResolver {

    public Optional<TrustedKnowledgeContext> resolve() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = servletAttributes.getRequest();
        String tenantId = header(request, "X-Tenant-Id");
        String subjectId = header(request, "X-Subject-Id");
        String subjectType = header(request, "X-Subject-Type");
        String userRoles = header(request, "X-User-Roles");
        String userPermissions = header(request, "X-User-Permissions");
        String snapshotId = header(request, "X-Authorization-Snapshot-Id");
        String traceId = header(request, "X-Trace-Id");
        String serviceAuthorization = header(request, "X-Service-Authorization");
        if (tenantId == null || subjectId == null || subjectType == null || userRoles == null || userPermissions == null
                || snapshotId == null || traceId == null || serviceAuthorization == null) {
            return Optional.empty();
        }
        return Optional.of(new TrustedKnowledgeContext(tenantId, subjectId, subjectType, userRoles, userPermissions,
                snapshotId, traceId, serviceAuthorization));
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value;
    }
}
