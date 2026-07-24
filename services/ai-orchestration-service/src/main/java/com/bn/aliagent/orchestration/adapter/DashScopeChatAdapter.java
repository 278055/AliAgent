package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class DashScopeChatAdapter implements ChatModelPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String apiKey;
    private final String endpoint;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();

    public DashScopeChatAdapter(String apiKey, String endpoint) { this.apiKey = apiKey; this.endpoint = endpoint; }
    @Override public String generate(OrchestrationContract.ExecutionContext context, String prompt) {
        if (apiKey == null || apiKey.isBlank()) throw new AdapterException(AdapterException.Category.CONFIGURATION, "DashScope API key is not configured");
        if (prompt == null || prompt.isBlank()) throw new AdapterException(AdapterException.Category.VALIDATION, "prompt must not be blank");
        try {
            String body = JSON.writeValueAsString(java.util.Map.of("model", "qwen-plus", "input", java.util.Map.of("messages", java.util.List.of(java.util.Map.of("role", "user", "content", prompt)))));
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(endpoint + "/api/v1/services/aigc/text-generation/generation"))
                    .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new AdapterException(AdapterException.Category.UNAVAILABLE, "DashScope model is unavailable");
            JsonNode output = JSON.readTree(response.body()).path("output");
            String content = output.path("text").asText(output.path("choices").path(0).path("message").path("content").asText());
            if (content.isBlank()) throw new AdapterException(AdapterException.Category.REMOTE, "DashScope returned an empty response");
            return content;
        } catch (AdapterException exception) { throw exception;
        } catch (Exception exception) { throw new AdapterException(AdapterException.Category.UNAVAILABLE, "DashScope model is unavailable", exception); }
    }
}
