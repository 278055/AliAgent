package com.bn.aliagent.conversation.core;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public record TrustedConversationRequestContext(String tenantId, String subjectId, String subjectType, String traceId, UUID requestId) {
    private static final String VERIFIED = "com.bn.platform.security.ServiceJwtAuthenticationFilter.verified";

    public static TrustedConversationRequestContext from(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(VERIFIED))) {
            throw new ConversationException("AUTH-401-001", "Service authentication is invalid");
        }
        try {
            return new TrustedConversationRequestContext(required(request, "X-Tenant-Id"), required(request, "X-Subject-Id"),
                    required(request, "X-Subject-Type"), required(request, "X-Trace-Id"), UUID.fromString(required(request, "X-Request-Id")));
        } catch (IllegalArgumentException exception) {
            throw new ConversationException("AUTH-400-001", "Trusted request context is invalid");
        }
    }

    private static String required(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
        return value;
    }
}
