import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import com.bn.platform.security.ServiceJwtSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import javax.crypto.SecretKey;

public final class P7E2eAcceptance {
    private static final String GATEWAY = "http://localhost:8080";
    private static final String IDENTITY_SECRET = "p7-e2e-identity-jwt-secret-must-be-at-least-32-bytes";
    private static final String SERVICE_SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";
    private static final String TENANT = "test-p7-e2e";
    private static final String MEMBER = "test-member-p7";
    private static final String STAFF = "test-staff-p7";
    private static final String SUPERVISOR = "test-supervisor-p7";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception { new P7E2eAcceptance().run(); }

    private void run() throws Exception {
        String member = identity(MEMBER, "MEMBER", List.of("MEMBER"));
        String staff = identity(STAFF, "STAFF", List.of("STAFF"));
        String supervisor = identity(SUPERVISOR, "STAFF", List.of("STAFF", "SUPERVISOR"));

        String conversation = id(post(GATEWAY + "/api/v1/conversations", member, "{\"title\":\"test-p7-e2e\"}"));
        UUID conversationId = UUID.fromString(conversation);
        internalTag(conversationId, "P7_E2E");

        String group = id(post(GATEWAY + "/api/v1/supervisor/skill-groups", supervisor, "{\"name\":\"test-p7-e2e-group\"}"));
        configureSkillGroup(UUID.fromString(group), supervisor);

        EventListener listener = new EventListener();
        WebSocket socket = http.newWebSocketBuilder().header("Authorization", "Bearer " + staff)
                .buildAsync(URI.create("ws://localhost:8080/api/v1/ws/conversations"), listener).join();
        socket.sendText("{\"eventType\":\"agent.presence\",\"conversationId\":\"" + conversation + "\",\"status\":\"ONLINE\"}", true).join();
        listener.require("agent.presence", 5000);

        UUID queueRequest = UUID.randomUUID();
        String queueResponse = post(GATEWAY + "/api/v1/conversations/" + conversation + "/human-queue", member,
                "{\"requestId\":\"" + queueRequest + "\"}", queueRequest.toString());
        String queueId = id(queueResponse);
        String offerResponse = post(GATEWAY + "/api/v1/supervisor/queue/" + queueId + "/offer", supervisor, "");
        String offerId = value(offerResponse, "offerId");
        UUID acceptRequest = UUID.randomUUID();
        post(GATEWAY + "/api/v1/agent/offers/" + offerId + "/accept", staff,
                "{\"requestId\":\"" + acceptRequest + "\"}", acceptRequest.toString());

        UUID memberMessageRequest = UUID.randomUUID();
        String suppressed = post(GATEWAY + "/api/v1/conversations/" + conversation + "/messages", member,
                "{\"content\":\"请帮我查询测试订单状态\",\"requestId\":\"" + memberMessageRequest + "\"}", memberMessageRequest.toString());
        require(suppressed.contains("\"aiMessage\":null"), "人工状态下不应创建公开 AI 消息: " + suppressed);

        UUID staffMessageId = UUID.randomUUID();
        socket.sendText("{\"eventType\":\"human.send\",\"conversationId\":\"" + conversation
                + "\",\"content\":\"客服已收到您的问题\",\"clientMessageId\":\"" + staffMessageId + "\"}", true).join();
        listener.require("human.message", 5000);

        String suggestionId = waitForSuggestion(conversation, staff);
        UUID actionRequest = UUID.randomUUID();
        String accepted = post(GATEWAY + "/api/v1/copilot/conversations/" + conversation + "/suggestions/" + suggestionId + ":accept",
                staff, "{\"requestId\":\"" + actionRequest + "\"}");
        require(accepted.contains("ACCEPTED"), "副驾建议未被接受: " + accepted);

        UUID releaseRequest = UUID.randomUUID();
        post(GATEWAY + "/api/v1/conversations/" + conversation + "/human-release", staff,
                "{\"requestId\":\"" + releaseRequest + "\"}", releaseRequest.toString());
        UUID aiRequest = UUID.randomUUID();
        String resumed = post(GATEWAY + "/api/v1/conversations/" + conversation + "/messages", member,
                "{\"content\":\"测试 AI 是否恢复\",\"requestId\":\"" + aiRequest + "\"}", aiRequest.toString());
        require(!resumed.contains("\"aiMessage\":null") && resumed.contains("generationId"), "释放后未恢复 AI 回复: " + resumed);
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        System.out.println("P7_E2E_ACCEPTED conversationId=" + conversation);
    }

    private String waitForSuggestion(String conversation, String staffToken) throws Exception {
        for (int attempt = 0; attempt < 15; attempt++) {
            String response = get(GATEWAY + "/api/v1/copilot/conversations/" + conversation + "/suggestions", staffToken);
            String id = optionalValue(response, "id");
            if (id != null && response.contains("GENERATED")) return id;
            Thread.sleep(1000);
        }
        throw new IllegalStateException("副驾建议未在时限内生成");
    }

    private void internalTag(UUID conversationId, String tag) throws Exception {
        String path = "/internal/api/v1/conversations/" + conversationId + "/verified-tags";
        String jwt = service(SERVICE_SECRET, "conversation-service", "POST:" + path);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8081" + path)).header("Content-Type", "application/json")
                .header("X-Service-Authorization", "Bearer " + jwt).header("X-Tenant-Id", TENANT).header("X-Subject-Id", "rule-engine")
                .header("X-Subject-Type", "SERVICE").header("X-Trace-Id", UUID.randomUUID().toString())
                .header("X-Request-Id", UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString("{\"tagCode\":\"" + tag + "\",\"source\":\"RULE_ENGINE\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("internal verified tag failed: " + response.statusCode() + " " + response.body());
    }

    private void configureSkillGroup(UUID groupId, String supervisorToken) throws Exception {
        String root = GATEWAY + "/api/v1/supervisor/skill-groups/" + groupId;
        put(root + "/tags", supervisorToken, "{\"tagCodes\":[\"P7_E2E\"]}");
        put(root + "/members/" + STAFF, supervisorToken, "{\"maxConcurrent\":2,\"enabled\":true}");
    }

    private String identity(String subject, String type, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(IDENTITY_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().setSubject(subject).claim("tenantId", TENANT).claim("subjectType", type).claim("roles", roles)
                .claim("permissions", List.of("KNOWLEDGE_READ")).signWith(SignatureAlgorithm.HS256, key).compact();
    }

    private String service(String secret, String audience, String scope) {
        ServiceJwtSupport support = new ServiceJwtSupport(secret);
        String token = support.issue("p7-e2e", audience, List.of(scope));
        support.verify(token, audience, scope);
        return token;
    }

    private String get(String url, String token) throws Exception { return request("GET", url, token, null, null); }
    private String post(String url, String token, String body) throws Exception { return post(url, token, body, null); }
    private String post(String url, String token, String body, String key) throws Exception { return request("POST", url, token, body, key); }
    private String put(String url, String token, String body) throws Exception { return request("PUT", url, token, body, null); }
    private String request(String method, String url, String token, String body, String key) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").timeout(Duration.ofSeconds(5));
        if (key != null) builder.header("Idempotency-Key", key);
        if ("GET".equals(method)) builder.GET(); else builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException(method + " " + url + " failed: " + response.statusCode() + " " + response.body());
        return response.body();
    }

    private String id(String json) { String value = optionalValue(json, "id"); if (value == null) throw new IllegalStateException("response lacks id: " + json); return value; }
    private String value(String json, String name) { String value = optionalValue(json, name); if (value == null) throw new IllegalStateException("response lacks " + name + ": " + json); return value; }
    private String optionalValue(String json, String name) {
        String marker = "\"" + name + "\":\""; int start = json.indexOf(marker);
        if (start < 0) return null; start += marker.length(); int end = json.indexOf('"', start); return end < 0 ? null : json.substring(start, end);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static final class EventListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final StringBuilder current = new StringBuilder();
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            current.append(data);
            if (last) { events.add(current.toString()); current.setLength(0); }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        void require(String type, long timeoutMillis) throws InterruptedException {
            String event = events.poll(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (event == null || !event.contains("\"eventType\":\"" + type + "\"")) throw new IllegalStateException("未收到实时事件 " + type + ": " + event);
        }
        public void onOpen(WebSocket socket) { socket.request(1); }
    }
}
