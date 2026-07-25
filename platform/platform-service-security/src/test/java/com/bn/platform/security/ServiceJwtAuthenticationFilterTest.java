package com.bn.platform.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServiceJwtAuthenticationFilterTest {
    private static final String SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";

    @Test
    void propagatesDownstreamServletExceptionAfterServiceJwtIsVerified() {
        ServiceJwtSupport jwtSupport = new ServiceJwtSupport(SECRET);
        ServiceJwtAuthenticationFilter filter = new ServiceJwtAuthenticationFilter(jwtSupport, "conversation-service");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/supervisor/skill-groups");
        request.addHeader("X-Service-Authorization", "Bearer " + jwtSupport.issue("gateway-service",
                "conversation-service", java.util.List.of("POST:/api/v1/supervisor/skill-groups")));

        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> {
                    throw new ServletException("downstream failure");
                }));
    }
}
