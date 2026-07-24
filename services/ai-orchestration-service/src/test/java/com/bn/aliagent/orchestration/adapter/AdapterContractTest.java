package com.bn.aliagent.orchestration.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.tool.MallReadToolAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AdapterContractTest {
    private final OrchestrationContract.ExecutionContext context = new OrchestrationContract.ExecutionContext(
            "test-p5-b-tenant", "123", "MEMBER", List.of("MEMBER"), List.of("order:read"), "trace-1",
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void mockModelIsTheSafeDefaultAndDashScopeRequiresKey() {
        assertEquals("Mock response: hello", new MockChatAdapter().generate(context, "hello"));
        assertThrows(AdapterException.class, () -> new DashScopeChatAdapter("", "http://localhost").generate(context, "hello"));
    }

    @Test
    void knowledgeAdapterPreservesCitationAndForwardsTrustedHeaders() throws Exception {
        try (StubServer server = new StubServer(exchange -> respond(exchange, 200,
                "{\"data\":{\"items\":[{\"documentId\":\"" + UUID.randomUUID() + "\",\"versionId\":\"" + UUID.randomUUID()
                        + "\",\"chunkId\":\"" + UUID.randomUUID() + "\",\"content\":\"policy\",\"score\":0.9}]}}"))) {
            KnowledgeServiceRetrievalAdapter adapter = new KnowledgeServiceRetrievalAdapter(server.url(), "service-jwt", 500, 2);
            assertEquals("policy", adapter.retrieve(context, "return policy", 3).get(0).content());
            assertEquals("Bearer service-jwt", server.lastAuthorization());
            assertEquals(context.authorizationSnapshotId().toString(), server.lastHeader("X-Authorization-Snapshot-Id"));
        }
    }

    @Test
    void mallToolsValidateIdsForwardSnapshotAndDoNotInventFactsWhenUnavailable() throws Exception {
        try (StubServer server = new StubServer(exchange -> respond(exchange, 200,
                "{\"data\":{\"id\":1,\"orderSn\":\"test-p5-b-order\",\"totalAmount\":\"99\",\"receiverPhone\":\"13800138000\"}}"))) {
            MallReadToolAdapter adapter = new MallReadToolAdapter(server.url(), "service-jwt", 500, 2);
            assertEquals("test-p5-b-order", adapter.readOrder(context, 1).data().get("orderSn"));
            assertEquals("***", adapter.readOrder(context, 1).data().get("receiverPhone"));
            assertEquals("Bearer service-jwt", server.lastAuthorization());
            assertThrows(AdapterException.class, () -> adapter.readOrder(context, 0));
        }
        MallReadToolAdapter unavailable = new MallReadToolAdapter("http://127.0.0.1:1", "service-jwt", 50, 1);
        assertThrows(AdapterException.class, () -> unavailable.readLogistics(context, 1));
    }

    @Test
    void conversationAdapterUsesReplyIdAndDoesNotRepeatConfirmedChunks() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (StubServer server = new StubServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"data\":{\"accepted\":true}}");
        })) {
            ConversationStreamAdapter adapter = new ConversationStreamAdapter(server.url(), "service-jwt", 500, 2);
            OrchestrationContract.StreamChunk chunk = new OrchestrationContract.StreamChunk(
                    context.replyMessageId(), context.generationId(), 0, "hello", false, null, List.of());
            adapter.append(context, chunk);
            adapter.append(context, chunk);
            assertEquals(1, calls.get());
            assertEquals(context.replyMessageId().toString(), server.requestBody().contains(context.replyMessageId().toString()) ? context.replyMessageId().toString() : "");
            assertFalse(server.requestBody().contains(context.sourceMessageId().toString()));
        }
    }

    @Test
    void conversationAdapterRejectsRegressingChunkIndex() throws Exception {
        try (StubServer server = new StubServer(exchange -> respond(exchange, 200, "{\"data\":{\"accepted\":true}}"))) {
            ConversationStreamAdapter adapter = new ConversationStreamAdapter(server.url(), "service-jwt", 500, 1);
            adapter.append(context, new OrchestrationContract.StreamChunk(context.replyMessageId(), context.generationId(), 1, "one", false, null, List.of()));
            assertThrows(AdapterException.class, () -> adapter.append(context,
                    new OrchestrationContract.StreamChunk(context.replyMessageId(), context.generationId(), 0, "zero", false, null, List.of())));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private volatile HttpExchange last;
        private volatile String body = "";
        private StubServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> { last = exchange; body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); handler.handle(exchange); });
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        String lastAuthorization() { return lastHeader("Authorization"); }
        String lastHeader(String name) { return last.getRequestHeaders().getFirst(name); }
        String requestBody() { return body; }
        @Override public void close() { server.stop(0); }
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
