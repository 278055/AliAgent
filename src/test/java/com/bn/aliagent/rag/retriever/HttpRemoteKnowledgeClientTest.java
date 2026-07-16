package com.bn.aliagent.rag.retriever;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRemoteKnowledgeClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private CapturedRequest captured;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(HttpRemoteKnowledgeClient.RETRIEVAL_PATH, this::respond);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsPublishedRequestShapeAndAllTrustedHeadersWithoutTenantInBody() {
        captured = new CapturedRequest(200, """
                {"code":200,"message":"","data":{"items":[{"documentId":"doc-1","versionId":"ver-1","chunkId":"chunk-1","content":"内容","score":0.1,"citation":{"documentId":"doc-1","versionId":"ver-1","chunkId":"chunk-1"}}]}}
                """);

        RemoteKnowledgeResult result = client().retrieve("退款规则", 7, context());

        assertEquals("POST", captured.method);
        assertEquals("退款规则", captured.body.path("query").asText());
        assertEquals(7, captured.body.path("topK").asInt());
        assertEquals(false, captured.body.has("tenantId"));
        assertEquals("tenant-a", header("X-Tenant-Id"));
        assertEquals("subject-a", header("X-Subject-Id"));
        assertEquals("MEMBER", header("X-Subject-Type"));
        assertEquals("USER", header("X-User-Roles"));
        assertEquals("KNOWLEDGE_READ", header("X-User-Permissions"));
        assertEquals("snapshot-a", header("X-Authorization-Snapshot-Id"));
        assertEquals("trace-a", header("X-Trace-Id"));
        assertEquals("Bearer service-jwt", header("X-Service-Authorization"));
        assertEquals("chunk-1", result.items().get(0).chunkId());
    }

    @Test
    void explicitNoGroundingResponseIsNotTreatedAsAnEmptySuccessfulCitationSet() {
        captured = new CapturedRequest(200, """
                {"code":200,"message":"没有可用的知识依据","data":{"items":[]}}
                """);

        RemoteKnowledgeResult result = client().retrieve("退款规则", 7, context());

        assertEquals(RemoteFailure.NO_GROUNDING, result.failure());
        assertEquals(0, result.items().size());
    }

    @Test
    void unauthorizedResponseIsClassifiedForImmediateLocalFallback() {
        captured = new CapturedRequest(401, "{\"code\":\"AUTH-401-001\"}");

        RemoteKnowledgeResult result = client().retrieve("退款规则", 7, context());

        assertEquals(RemoteFailure.UNAUTHORIZED, result.failure());
    }

    @Test
    void otherClientErrorIsClassifiedForImmediateLocalFallback() {
        captured = new CapturedRequest(429, "{\"code\":\"SYSTEM-429-001\"}");

        RemoteKnowledgeResult result = client().retrieve("退款规则", 7, context());

        assertEquals(RemoteFailure.HTTP_ERROR, result.failure());
    }

    @Test
    void invalidResponseIsClassifiedAsProtocolFailure() {
        captured = new CapturedRequest(200, "{\"code\":200,\"data\":{\"items\":[{}]}}");

        RemoteKnowledgeResult result = client().retrieve("退款规则", 7, context());

        assertEquals(RemoteFailure.PROTOCOL_ERROR, result.failure());
    }

    private HttpRemoteKnowledgeClient client() {
        return new HttpRemoteKnowledgeClient(HttpClient.newHttpClient(), objectMapper,
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(2));
    }

    private TrustedKnowledgeContext context() {
        return new TrustedKnowledgeContext("tenant-a", "subject-a", "MEMBER", "USER", "KNOWLEDGE_READ",
                "snapshot-a", "trace-a", "Bearer service-jwt");
    }

    private void respond(HttpExchange exchange) throws IOException {
        captured.method = exchange.getRequestMethod();
        captured.body = objectMapper.readTree(exchange.getRequestBody());
        captured.headers = new java.util.HashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> captured.headers.put(key, values.get(0)));
        byte[] response = captured.response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(captured.status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private String header(String name) {
        return captured.headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
    }

    private static final class CapturedRequest {
        private final int status;
        private final String response;
        private String method;
        private JsonNode body;
        private java.util.Map<String, String> headers;

        private CapturedRequest(int status, String response) {
            this.status = status;
            this.response = response;
        }
    }
}
