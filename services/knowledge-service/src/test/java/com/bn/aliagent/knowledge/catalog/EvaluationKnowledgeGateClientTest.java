package com.bn.aliagent.knowledge.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.platform.security.ServiceJwtSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class EvaluationKnowledgeGateClientTest {
    private static final String SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";
    private static final String PATH = "/internal/api/v1/evaluation/gate-proofs:verify";

    @Test
    void acceptsStrictTrueAndSendsKnowledgeArtifact() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        try (Server server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("X-Service-Authorization"));
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            reply(exchange, 200, "{\"data\":{\"accepted\":true}}");
        })) {
            client(server).requirePass("tenant-a", UUID.randomUUID(), "manifest", "policy", proof());
            String token = authorization.get().substring("Bearer ".length());
            new ServiceJwtSupport(SECRET).verify(token, "evaluation-service", "POST:" + PATH);
            assertEquals("tenant-a", tenant.get());
            assertEquals(true, requestBody.get().contains("\"artifactType\":\"KNOWLEDGE\""));
        }
    }

    @Test void rejectsFalseDecision() throws Exception { try (Server server = server(e -> reply(e, 200, "{\"data\":{\"accepted\":false}}"))) { assertThrows(SecurityException.class, () -> call(client(server))); } }
    @Test void rejectsNonSuccessResponse() throws Exception { try (Server server = server(e -> reply(e, 503, "{}"))) { assertThrows(SecurityException.class, () -> call(client(server))); } }
    @Test void rejectsMalformedResponse() throws Exception { try (Server server = server(e -> reply(e, 200, "{\"data\":{\"accepted\":1}}"))) { assertThrows(SecurityException.class, () -> call(client(server))); } }
    @Test void rejectsUnavailableServer() { assertThrows(SecurityException.class, () -> call(new EvaluationKnowledgeGateClient("http://127.0.0.1:1", SECRET))); }

    private static void call(EvaluationKnowledgeGateClient client) { client.requirePass("tenant-a", UUID.randomUUID(), "manifest", "policy", proof()); }
    private static String proof() { return "{\"proofId\":\"" + UUID.randomUUID() + "\",\"canonicalPayload\":\"payload\",\"signature\":\"signature\",\"keyId\":\"key-1\"}"; }
    private static EvaluationKnowledgeGateClient client(Server server) { return new EvaluationKnowledgeGateClient("http://127.0.0.1:" + server.port(), SECRET); }
    private static Server server(Handler handler) throws Exception { return new Server(handler); }
    private static void reply(HttpExchange exchange, int status, String body) throws java.io.IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws java.io.IOException; }
    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private Server(Handler handler) throws Exception { server = HttpServer.create(new InetSocketAddress(0), 0); server.createContext(PATH, handler::handle); server.start(); }
        private int port() { return server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
}
