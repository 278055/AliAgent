package com.bn.aliagent.insight.topic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 仅发送匿名样本给 DashScope；调用失败由 TopicPublicationService 降级为待命名。 */
public final class DashScopeTopicNamingPort implements TopicNamingPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String apiKey;
    private final String endpoint;
    private final RequestSender sender;

    public DashScopeTopicNamingPort(String apiKey, String endpoint) {
        this(apiKey, endpoint, body -> send(apiKey, endpoint, body));
    }

    DashScopeTopicNamingPort(String apiKey, String endpoint, RequestSender sender) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.sender = sender;
    }

    @Override
    public TopicNaming name(String tenantId, List<String> anonymizedSamples, String modelVersion, String promptVersion) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("DashScope API Key 未配置");
        if (tenantId == null || tenantId.isBlank() || anonymizedSamples == null || anonymizedSamples.isEmpty()) {
            throw new IllegalArgumentException("主题命名需要租户和匿名样本");
        }
        try {
            String prompt = "仅基于以下匿名样本返回 JSON，字段为 name 和 summary；不得推断事实：" + JSON.writeValueAsString(anonymizedSamples);
            String body = JSON.writeValueAsString(Map.of("model", modelVersion, "input", Map.of("messages", List.of(Map.of("role", "user", "content", prompt))), "parameters", Map.of("result_format", "message")));
            Map<String, Object> root = JSON.readValue(sender.send(body), new TypeReference<>() { });
            Map<String, Object> output = cast(root.get("output"));
            String content = String.valueOf(output.get("text"));
            Map<String, Object> result = JSON.readValue(content, new TypeReference<>() { });
            return new TopicNaming(required(result, "name"), required(result, "summary"));
        } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DashScope 主题命名失败", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("DashScope 返回无效主题", exception);
        }
    }

    private static String send(String apiKey, String endpoint, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("DashScope HTTP " + response.statusCode());
        return response.body();
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("缺少 output");
        return (Map<String, Object>) map;
    }
    private static String required(Map<String, Object> value, String key) {
        Object result = value.get(key);
        if (result == null || result.toString().isBlank()) throw new IllegalArgumentException("缺少 " + key);
        return result.toString();
    }
    @FunctionalInterface interface RequestSender { String send(String body) throws Exception; }
}
