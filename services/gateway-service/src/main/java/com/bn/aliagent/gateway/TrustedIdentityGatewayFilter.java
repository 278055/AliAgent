package com.bn.aliagent.gateway;

import com.bn.platform.security.ServiceJwtSupport;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
final class TrustedIdentityGatewayFilter implements GlobalFilter, Ordered {
    private static final List<String> INTERNAL_HEADERS = List.of("X-Tenant-Id", "X-Subject-Id", "X-Subject-Type",
            "X-User-Roles", "X-User-Permissions", "X-Authorization-Snapshot-Id", "X-Service-Authorization",
            "X-Trace-Id", "X-Request-Id");
    private final IdentityJwtSupport identityJwt;
    private final ServiceJwtSupport serviceJwt;

    TrustedIdentityGatewayFilter(
            @Value("${IDENTITY_JWT_SECRET:test-identity-jwt-secret-must-be-at-least-32-bytes}") String identitySecret,
            @Value("${SERVICE_JWT_SECRET:test-service-jwt-secret-must-be-at-least-32-bytes}") String serviceSecret) {
        this.identityJwt = new IdentityJwtSupport(identitySecret);
        this.serviceJwt = new ServiceJwtSupport(serviceSecret);
    }

    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Bearer token required");
            TrustedIdentity identity = identityJwt.verify(authorization.substring(7));
            String traceId = UUID.randomUUID().toString();
            String requestId = UUID.randomUUID().toString();
            String scope = exchange.getRequest().getMethod().name() + ":" + exchange.getRequest().getURI().getPath();
            var request = exchange.getRequest().mutate().headers(headers -> {
                INTERNAL_HEADERS.forEach(headers::remove);
                headers.set("X-Tenant-Id", identity.tenantId());
                headers.set("X-Subject-Id", identity.subjectId());
                headers.set("X-Subject-Type", identity.subjectType());
                headers.set("X-User-Roles", String.join(",", identity.roles()));
                headers.set("X-User-Permissions", String.join(",", identity.permissions()));
                headers.set("X-Trace-Id", traceId);
                headers.set("X-Request-Id", requestId);
                headers.set("X-Service-Authorization", "Bearer " + serviceJwt.issue("gateway-service", "conversation-service", List.of(scope)));
            }).build();
            exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
            exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
            return chain.filter(exchange.mutate().request(request).build());
        } catch (RuntimeException exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
