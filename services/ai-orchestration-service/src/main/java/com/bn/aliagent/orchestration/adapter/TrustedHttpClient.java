package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.StringJoiner;

public final class TrustedHttpClient {
    private final HttpClient client;
    private final String serviceJwt;
    private final int timeoutMs;
    private final int attempts;
    public TrustedHttpClient(String serviceJwt, int timeoutMs, int attempts) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.serviceJwt = serviceJwt;
        this.timeoutMs = timeoutMs;
        this.attempts = Math.max(1, attempts);
    }
    public String get(String url, OrchestrationContract.ExecutionContext context) { return send("GET", url, null, context); }
    public String post(String url, String body, OrchestrationContract.ExecutionContext context) { return send("POST", url, body, context); }
    private String send(String method, String url, String body, OrchestrationContract.ExecutionContext context) {
        if (serviceJwt == null || serviceJwt.isBlank()) throw new AdapterException(AdapterException.Category.CONFIGURATION, "service JWT is not configured");
        for (int attempt = 1; attempt <= attempts; attempt++) try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + serviceJwt).header("X-Tenant-Id", context.tenantId())
                    .header("X-Subject-Id", context.subjectId()).header("X-Subject-Type", context.subjectType())
                    .header("X-User-Roles", join(context.roles())).header("X-User-Permissions", join(context.permissions()))
                    .header("X-Trace-Id", context.traceId()).header("X-Authorization-Snapshot-Id", context.authorizationSnapshotId().toString())
                    .header("X-Request-Id", context.requestId().toString()).header("Content-Type", "application/json");
            HttpResponse<String> response = client.send("POST".equals(method) ? request.POST(HttpRequest.BodyPublishers.ofString(body)).build() : request.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return response.body();
            if (response.statusCode() < 500 || attempt == attempts) throw new AdapterException(AdapterException.Category.REMOTE, "downstream rejected request: " + response.statusCode());
        } catch (AdapterException exception) { throw exception;
        } catch (Exception exception) { if (attempt == attempts) throw new AdapterException(AdapterException.Category.UNAVAILABLE, "downstream service is unavailable", exception); }
        throw new AdapterException(AdapterException.Category.UNAVAILABLE, "downstream service is unavailable");
    }
    private static String join(java.util.List<String> values) { return new StringJoiner(",").add(String.join(",", values)).toString(); }
}
