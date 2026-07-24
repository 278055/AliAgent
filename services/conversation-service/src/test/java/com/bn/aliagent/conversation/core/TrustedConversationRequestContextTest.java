package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedConversationRequestContextTest {
    @Test
    void rejectsUnverifiedRequestBeforeConsumingHeaders() {
        MockHttpServletRequest request = request();
        assertThrows(ConversationException.class, () -> TrustedConversationRequestContext.from(request));
    }

    @Test
    void readsTenantAndRequestIdOnlyAfterVerification() {
        MockHttpServletRequest request = request();
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);
        TrustedConversationRequestContext context = TrustedConversationRequestContext.from(request);
        assertEquals("test-tenant", context.tenantId());
        assertEquals("test-subject", context.subjectId());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "test-tenant");
        request.addHeader("X-Subject-Id", "test-subject");
        request.addHeader("X-Subject-Type", "MEMBER");
        request.addHeader("X-Trace-Id", UUID.randomUUID().toString());
        request.addHeader("X-Request-Id", UUID.randomUUID().toString());
        return request;
    }
}
