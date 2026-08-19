package com.bn.aliagent.insight.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpMallEvidencePortTest {
    @Test
    void sendsServiceJwtAndTrustedTenantToMall() throws Exception {
        AtomicReference<String> jwt = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> role = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/insights/evidence/test-evidence", exchange -> {
            jwt.set(exchange.getRequestHeaders().getFirst("X-Service-Authorization"));
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            role.set(exchange.getRequestHeaders().getFirst("X-User-Roles"));
            byte[] body = "{\"tenantId\":\"test-tenant\",\"evidenceRef\":\"test-evidence\",\"status\":\"PAID\",\"reference\":\"test-ref\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var port = new HttpMallEvidencePort("http://localhost:" + server.getAddress().getPort());
            var result = port.verify(request(), supervisor());
            assertEquals("Bearer service-jwt", jwt.get());
            assertEquals("test-tenant", tenant.get());
            assertEquals("SUPERVISOR", role.get());
            assertEquals("PAID", result.status());
        } finally { server.stop(0); }
    }

    @Test
    void rejectsMallFailure() {
        var port = new HttpMallEvidencePort("http://localhost:1");
        assertThrows(IllegalStateException.class, () -> port.verify(request(), supervisor()));
    }

    private MallEvidenceVerificationClient.Request request() { return new MallEvidenceVerificationClient.Request(java.util.UUID.randomUUID(), "test-tenant", "test-evidence", "reason", "service-jwt"); }
    private MallEvidenceVerificationClient.Supervisor supervisor() { return new MallEvidenceVerificationClient.Supervisor("9", "test-tenant", java.util.Set.of("SUPERVISOR")); }
}
