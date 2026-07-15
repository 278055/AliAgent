package com.macro.mall.portal.internal.read;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalReadAuthenticationFilterTest {
    private static final String SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";

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
                new HeaderUserSnapshotResolver(), new Hs256ServiceIdentityVerifier(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1");
        request.addHeader("X-Service-Authorization", validAuthorization("mall.internal.read"));
        request.addHeader("X-Tenant-Id", "tenant-test");
        request.addHeader("X-Subject-Id", "200");
        request.addHeader("X-Subject-Type", "MEMBER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertNotNull(request.getAttribute(UserSnapshot.REQUEST_ATTRIBUTE));
    }

    @Test
    void rejectsValidServiceJwtWithoutUserSnapshot() throws Exception {
        InternalReadAuthenticationFilter filter = new InternalReadAuthenticationFilter(
                new HeaderUserSnapshotResolver(), new Hs256ServiceIdentityVerifier(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1");
        request.addHeader("X-Service-Authorization", validAuthorization("mall.internal.read"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsCompleteUserSnapshotWhenServiceScopeIsInsufficient() throws Exception {
        InternalReadAuthenticationFilter filter = new InternalReadAuthenticationFilter(
                new HeaderUserSnapshotResolver(), new Hs256ServiceIdentityVerifier(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/mall/orders/1");
        request.addHeader("X-Service-Authorization", validAuthorization("other.scope"));
        request.addHeader("X-Tenant-Id", "tenant-test");
        request.addHeader("X-Subject-Id", "200");
        request.addHeader("X-Subject-Type", "MEMBER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    private String validAuthorization(String scope) {
        Instant now = Instant.now();
        String token = Jwts.builder().setSubject("gateway-service").claim("caller", "gateway-service")
                .setAudience("mall").claim("scopes", Arrays.asList(scope)).setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(300))).setId(UUID.randomUUID().toString())
                .signWith(SignatureAlgorithm.HS256, SECRET).compact();
        return "Bearer " + token;
    }
}
