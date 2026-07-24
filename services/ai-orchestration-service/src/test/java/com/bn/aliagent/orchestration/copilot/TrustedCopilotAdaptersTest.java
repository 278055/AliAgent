package com.bn.aliagent.orchestration.copilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrustedCopilotAdaptersTest {
    @Test
    void loadsLinkedOrderOnlyFromTrustedConversationContext() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/api/v1/conversations/", exchange -> respond(exchange, 200,
                "{\"tenantId\":\"test-tenant\",\"conversationId\":\"" + CONVERSATION + "\",\"collaborationState\":\"HUMAN_ACTIVE\",\"assignedAgentId\":\"staff-1\",\"messages\":[\"客户消息\"],\"linkedOrderId\":101}"));
        server.start();
        try {
            var adapter = new TrustedConversationContextAdapter(url(server), "test-jwt", 500, 1);
            var context = adapter.load("test-tenant", CONVERSATION);

            assertEquals(101L, context.linkedOrderId());
            assertEquals("staff-1", context.assignedAgentId());
        } finally { server.stop(0); }
    }

    @Test
    void staffMessageUsesActionRequestIdAsTheStableClientMessageId() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/api/v1/conversations/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"code\":200}");
        });
        server.start();
        try {
            UUID requestId = UUID.randomUUID();
            new TrustedStaffMessageAdapter(url(server), "test-jwt", 500, 1)
                    .send("test-tenant", CONVERSATION, "staff-1", "建议回复", requestId);

            assertTrue(body.get().contains(requestId.toString()));
            assertTrue(body.get().contains("建议回复"));
        } finally { server.stop(0); }
    }

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static String url(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
