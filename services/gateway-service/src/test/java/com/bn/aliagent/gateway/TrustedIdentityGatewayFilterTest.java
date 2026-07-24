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

    @Test
    void copilotRouteIsIssuedForTheOrchestrationServiceAndRejectsMembers() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        String member = identityToken(identitySecret, "member-1", "MEMBER", List.of("MEMBER"));
        var memberExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/copilot/conversations/" + java.util.UUID.randomUUID() + "/suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + member).build());

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret).filter(memberExchange, value -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, memberExchange.getResponse().getStatusCode());
        String staff = identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF"));
        var staffExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/copilot/conversations/" + java.util.UUID.randomUUID() + "/suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff).build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret).filter(staffExchange, value -> {
            forwarded.set(value.getRequest().getHeaders());
            return reactor.core.publisher.Mono.empty();
        }).block();

        new ServiceJwtSupport(serviceSecret).verify(forwarded.get().getFirst("X-Service-Authorization").substring(7),
                "ai-orchestration-service", "GET:" + staffExchange.getRequest().getPath().value());
    }

    private static String identityToken(String secret, String subject, String type, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).claim("subjectType", type).claim("tenantId", "test-p4-tenant")
                .claim("roles", roles).claim("permissions", List.of()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .id("test-jti-" + subject).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
