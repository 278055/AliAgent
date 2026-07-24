package com.bn.aliagent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.bn.platform.security.ServiceJwtSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class TrustedIdentityGatewayFilterTest {
    @Test
    void forgedInternalHeadersAreOverwrittenAndServiceJwtIsScoped() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        Instant now = Instant.now();
        String token = Jwts.builder().subject("member-1").claim("loginName", "member").claim("subjectType", "MEMBER")
                .claim("tenantId", "test-p4-tenant").claim("roles", List.of("MEMBER"))
                .claim("permissions", List.of("conversation:write")).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60))).id("test-jti")
                .signWith(Keys.hmacShaKeyFor(identitySecret.getBytes(StandardCharsets.UTF_8))).compact();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("X-Tenant-Id", "forged-tenant")
                .header("X-Service-Authorization", "Bearer forged").build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret).filter(exchange, value -> {
            forwarded.set(value.getRequest().getHeaders());
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertEquals("test-p4-tenant", forwarded.get().getFirst("X-Tenant-Id"));
        assertEquals("member-1", forwarded.get().getFirst("X-Subject-Id"));
        assertNotEquals("Bearer forged", forwarded.get().getFirst("X-Service-Authorization"));
        new ServiceJwtSupport(serviceSecret).verify(forwarded.get().getFirst("X-Service-Authorization").substring(7),
                "conversation-service", "GET:/api/v1/conversations");
    }
}
