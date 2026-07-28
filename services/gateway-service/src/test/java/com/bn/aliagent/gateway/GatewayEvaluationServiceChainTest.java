package com.bn.aliagent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.bn.platform.security.ServiceJwtSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.context.WebServerApplicationContext;

class GatewayEvaluationServiceChainTest {
    private static final String IDENTITY_SECRET = "gateway-e2e-identity-jwt-secret-must-be-at-least-32-bytes";
    private static final String SERVICE_SECRET = "gateway-e2e-service-jwt-secret-must-be-at-least-32-bytes";
    private static final String TENANT_ID = "trusted-e2e-tenant";
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer evaluationBackend;
    private ConfigurableApplicationContext gateway;
    private final AtomicInteger backendHits = new AtomicInteger();
    private final AtomicReference<Map<String, List<String>>> receivedHeaders = new AtomicReference<>();

    @AfterEach
    void stopServers() {
        if (gateway != null) gateway.close();
        if (evaluationBackend != null) evaluationBackend.stop(0);
    }

    @Test
    void memberIsRejectedBeforeTheEvaluationBackendIsReached() throws Exception {
        startGateway();

        HttpResponse<String> response = get(identity("member-1", "MEMBER", List.of("MEMBER")));

        assertEquals(403, response.statusCode());
        assertEquals(0, backendHits.get());
    }

    @Test
    void evaluationAdminIsRoutedWithGatewayIssuedServiceJwtAndTrustedIdentityHeaders() throws Exception {
        startGateway();

        HttpResponse<String> response = get(identity("staff-1", "STAFF", List.of("STAFF", "EVALUATION_ADMIN")));

        assertEquals(200, response.statusCode());
        assertEquals(1, backendHits.get());
        Map<String, List<String>> headers = receivedHeaders.get();
        assertEquals(TENANT_ID, header(headers, "X-Tenant-Id"));
        assertEquals("staff-1", header(headers, "X-Subject-Id"));
        assertEquals("STAFF", header(headers, "X-Subject-Type"));
        assertEquals("STAFF,EVALUATION_ADMIN", header(headers, "X-User-Roles"));
        new ServiceJwtSupport(SERVICE_SECRET).verify(bearer(header(headers, "X-Service-Authorization")),
                "evaluation-service", "GET:/api/v1/evaluation/candidates");
    }

    @Test
    void forgedInternalIdentityHeadersAreOverwrittenBeforeReachingEvaluation() throws Exception {
        startGateway();

        HttpResponse<String> response = get(identity("staff-1", "STAFF", List.of("STAFF", "EVALUATION_ADMIN")), Map.of(
                "X-Tenant-Id", "forged-tenant", "X-Subject-Id", "forged-subject", "X-Subject-Type", "MEMBER",
                "X-User-Roles", "MEMBER", "X-Service-Authorization", "Bearer forged-service-token"));

        assertEquals(200, response.statusCode());
        Map<String, List<String>> headers = receivedHeaders.get();
        assertEquals(TENANT_ID, header(headers, "X-Tenant-Id"));
        assertEquals("staff-1", header(headers, "X-Subject-Id"));
        assertEquals("STAFF", header(headers, "X-Subject-Type"));
        assertEquals("STAFF,EVALUATION_ADMIN", header(headers, "X-User-Roles"));
        assertNotEquals("Bearer forged-service-token", header(headers, "X-Service-Authorization"));
    }

    private void startGateway() throws IOException {
        evaluationBackend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        evaluationBackend.createContext("/api/v1/evaluation/candidates", this::recordEvaluationRequest);
        evaluationBackend.start();
        String backendUrl = "http://127.0.0.1:" + evaluationBackend.getAddress().getPort();
        gateway = new SpringApplicationBuilder(GatewayServiceApplication.class)
                .properties("server.port=0", "IDENTITY_JWT_SECRET=" + IDENTITY_SECRET, "SERVICE_JWT_SECRET=" + SERVICE_SECRET,
                        "EVALUATION_HTTP_URI=" + backendUrl, "gateway.knowledge.base-url=http://127.0.0.1:1")
                .run();
    }

    private void recordEvaluationRequest(HttpExchange exchange) throws IOException {
        backendHits.incrementAndGet();
        receivedHeaders.set(exchange.getRequestHeaders());
        byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpResponse<String> get(String token) throws Exception {
        return get(token, Map.of());
    }

    private HttpResponse<String> get(String token, Map<String, String> headers) throws Exception {
        int gatewayPort = ((WebServerApplicationContext) gateway).getWebServer().getPort();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gatewayPort
                + "/api/v1/evaluation/candidates")).timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET();
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String identity(String subject, String type, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject).claim("tenantId", TENANT_ID).claim("subjectType", type).claim("roles", roles)
                .claim("permissions", List.of("evaluation:manage")).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .id("gateway-e2e-" + subject).signWith(Keys.hmacShaKeyFor(IDENTITY_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).findFirst()
                .map(entry -> entry.getValue().get(0)).orElse(null);
    }

    private static String bearer(String value) {
        return value.substring("Bearer ".length());
    }
}
