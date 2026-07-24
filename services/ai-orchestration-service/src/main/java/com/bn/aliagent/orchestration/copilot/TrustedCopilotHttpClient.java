package com.bn.aliagent.orchestration.copilot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

final class TrustedCopilotHttpClient {
    private final HttpClient client;
    private final String serviceJwt;
    private final int timeoutMs;
    private final int attempts;

    TrustedCopilotHttpClient(String serviceJwt, int timeoutMs, int attempts) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.serviceJwt = serviceJwt;
        this.timeoutMs = timeoutMs;
        this.attempts = Math.max(1, attempts);
    }

    String get(String url, String tenantId, String subjectId, String subjectType, UUID requestId) {
        return send("GET", url, null, tenantId, subjectId, subjectType, requestId);
    }

    String post(String url, String body, String tenantId, String subjectId, String subjectType, UUID requestId) {
        return send("POST", url, body, tenantId, subjectId, subjectType, requestId);
    }

    private String send(String method, String url, String body, String tenantId, String subjectId, String subjectType, UUID requestId) {
        if (serviceJwt == null || serviceJwt.isBlank()) throw new CopilotException("service JWT is not configured");
        for (int attempt = 1; attempt <= attempts; attempt++) try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
                    .header("X-Service-Authorization", "Bearer " + serviceJwt)
                    .header("X-Tenant-Id", tenantId).header("X-Subject-Id", subjectId).header("X-Subject-Type", subjectType)
                    .header("X-Trace-Id", requestId.toString()).header("X-Request-Id", requestId.toString())
                    .header("Content-Type", "application/json");
            HttpResponse<String> response = client.send("POST".equals(method) ? builder.POST(HttpRequest.BodyPublishers.ofString(body)).build() : builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return response.body();
            if (response.statusCode() < 500 || attempt == attempts) throw new CopilotException("trusted downstream rejected request");
        } catch (CopilotException exception) { throw exception;
        } catch (Exception exception) { if (attempt == attempts) throw new CopilotException("trusted downstream is unavailable"); }
        throw new CopilotException("trusted downstream is unavailable");
    }
}
