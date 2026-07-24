package com.bn.aliagent.conversation.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrustedMallOrderOwnershipVerifierTest {
    @Test
    void forwardsTrustedMemberIdentityToTheMallOrderReadEndpoint() throws Exception {
        AtomicReference<String> tenant = new AtomicReference<>();
        AtomicReference<String> subject = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/mall/orders/101", exchange -> {
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            subject.set(exchange.getRequestHeaders().getFirst("X-Subject-Id"));
            authorization.set(exchange.getRequestHeaders().getFirst("X-Service-Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var verifier = new TrustedMallOrderOwnershipVerifier("http://localhost:" + server.getAddress().getPort(), "test-mall-jwt", 1000);
            verifier.verifyMemberOwnsOrder(new TrustedConversationRequestContext("test-tenant", "101", "MEMBER", "test-trace", UUID.randomUUID()), 101L);

            assertEquals("test-tenant", tenant.get());
            assertEquals("101", subject.get());
            assertEquals("Bearer test-mall-jwt", authorization.get());
        } finally {
            server.stop(0);
        }
    }
}
