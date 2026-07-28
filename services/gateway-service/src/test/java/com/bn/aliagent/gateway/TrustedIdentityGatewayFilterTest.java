package com.bn.aliagent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString()).filter(exchange, value -> {
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

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString()).filter(memberExchange, value -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, memberExchange.getResponse().getStatusCode());
        String staff = identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF"));
        var staffExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/copilot/conversations/" + java.util.UUID.randomUUID() + "/suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff).build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString()).filter(staffExchange, value -> {
            forwarded.set(value.getRequest().getHeaders());
            return reactor.core.publisher.Mono.empty();
        }).block();

        new ServiceJwtSupport(serviceSecret).verify(forwarded.get().getFirst("X-Service-Authorization").substring(7),
                "ai-orchestration-service", "GET:" + staffExchange.getRequest().getPath().value());
    }

    @Test
    void evaluationCandidateRouteUsesEvaluationAudienceAndRejectsMembers() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        var memberExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/evaluation/candidates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken(identitySecret, "member-1", "MEMBER", List.of("MEMBER"))).build());
        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString())
                .filter(memberExchange, value -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, memberExchange.getResponse().getStatusCode());

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/evaluation/candidates")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF", "EVALUATION_ADMIN")))
                .header("X-User-Roles", "MEMBER").build());
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString()).filter(exchange, value -> { forwarded.set(value.getRequest().getHeaders()); return reactor.core.publisher.Mono.empty(); }).block();
        assertEquals("STAFF,EVALUATION_ADMIN", forwarded.get().getFirst("X-User-Roles"));
        new ServiceJwtSupport(serviceSecret).verify(forwarded.get().getFirst("X-Service-Authorization").substring(7), "evaluation-service", "GET:/api/v1/evaluation/candidates");
    }

    @Test
    void evaluationRoutesUseExactRootSegmentRoleMapping() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/versions", "VERSION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/versions/release-1", "VERSION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/gate", "VERSION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/gate/proofs", "VERSION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/dashscope", "DASHSCOPE_APPROVER");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/dashscope/approvals", "DASHSCOPE_APPROVER");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/candidates", "EVALUATION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/datasets", "EVALUATION_ADMIN");
        assertEvaluationRole(identitySecret, serviceSecret, "/api/v1/evaluation/runs", "EVALUATION_ADMIN");
    }

    @Test
    void evaluationRejectsStaffWithoutRoleAndUnauthenticatedRequests() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        assertEvaluationStatus(identitySecret, serviceSecret, "/api/v1/evaluation/candidates",
                identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF")), org.springframework.http.HttpStatus.FORBIDDEN);
        assertEvaluationStatus(identitySecret, serviceSecret, "/api/v1/evaluation/candidates", null, org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertEvaluationStatus(identitySecret, serviceSecret, "/api/v1/evaluation/candidates", "not-a-jwt", org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    private static void assertEvaluationRole(String identitySecret, String serviceSecret, String path, String role) {
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF", role))).build());
        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> java.util.UUID.randomUUID().toString())
                .filter(exchange, value -> { forwarded.set(value.getRequest().getHeaders()); return reactor.core.publisher.Mono.empty(); }).block();
        assertNull(exchange.getResponse().getStatusCode());
        new ServiceJwtSupport(serviceSecret).verify(forwarded.get().getFirst("X-Service-Authorization").substring(7), "evaluation-service", "GET:" + path);
    }

    private static void assertEvaluationStatus(String identitySecret, String serviceSecret, String path, String token,
            org.springframework.http.HttpStatus expectedStatus) {
        var request = MockServerHttpRequest.get(path).header("X-User-Roles", "EVALUATION_ADMIN")
                .header("X-User-Permissions", "evaluation:manage");
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var exchange = MockServerWebExchange.from(request.build());
        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, requestId) -> java.util.UUID.randomUUID().toString())
                .filter(exchange, value -> reactor.core.publisher.Mono.empty()).block();
        assertEquals(expectedStatus, exchange.getResponse().getStatusCode());
    }

    @Test
    void 普通会话请求不依赖知识快照服务() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken(identitySecret, "member-1", "MEMBER", List.of("MEMBER"))).build());
        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> { throw new IllegalStateException("knowledge unavailable"); })
                .filter(exchange, value -> reactor.core.publisher.Mono.empty()).block();
        assertNull(exchange.getResponse().getStatusCode());
        assertNull(exchange.getResponse().getHeaders().getFirst("X-Authorization-Snapshot-Id"));
    }

    @Test
    void 副驾请求在可信快照无法持久化时拒绝转发() {
        String identitySecret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        String serviceSecret = "test-service-jwt-secret-must-be-at-least-32-bytes";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/copilot/conversations/" + java.util.UUID.randomUUID() + "/suggestions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken(identitySecret, "staff-1", "STAFF", List.of("STAFF"))).build());

        new TrustedIdentityGatewayFilter(identitySecret, serviceSecret, (identity, trace, request) -> { throw new IllegalStateException("knowledge unavailable"); })
                .filter(exchange, value -> reactor.core.publisher.Mono.empty()).block();

        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private static String identityToken(String secret, String subject, String type, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).claim("subjectType", type).claim("tenantId", "test-p4-tenant")
                .claim("roles", roles).claim("permissions", List.of()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .id("test-jti-" + subject).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
