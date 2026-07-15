package com.macro.mall.portal.internal.read;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalReadAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestWithoutServiceIdentity() throws Exception {
        InternalReadAuthenticationFilter filter = new InternalReadAuthenticationFilter(
                new HeaderUserSnapshotResolver(), (authorization, audience, scope) -> {
                    throw new InternalAuthenticationException("invalid service identity");
                });
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1"), response,
                new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void requiresACompleteUserSnapshotAfterServiceIdentityIsAccepted() throws Exception {
        InternalReadAuthenticationFilter filter = new InternalReadAuthenticationFilter(
                new HeaderUserSnapshotResolver(), (authorization, audience, scope) -> { });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1");
        request.addHeader("X-Service-Authorization", "Bearer service-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void acceptsVerifiedServiceIdentityAndMemberSnapshot() throws Exception {
        InternalReadAuthenticationFilter filter = new InternalReadAuthenticationFilter(
                new HeaderUserSnapshotResolver(), (authorization, audience, scope) -> { });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1");
        request.addHeader("X-Service-Authorization", "Bearer service-token");
        request.addHeader("X-Tenant-Id", "tenant-test");
        request.addHeader("X-Subject-Id", "200");
        request.addHeader("X-Subject-Type", "MEMBER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNotNull(request.getAttribute(UserSnapshot.REQUEST_ATTRIBUTE));
    }
}
