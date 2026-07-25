package com.bn.aliagent.orchestration.copilot;

import com.bn.platform.security.ServiceJwtSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

final class TrustedCopilotHttpClient {
    private final HttpClient client;
    private final String serviceJwt;
    private final ServiceJwtSupport jwtSupport;
    private final String audience;
    private final int timeoutMs;
    private final int attempts;

    TrustedCopilotHttpClient(String serviceJwt, String audience, int timeoutMs, int attempts) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        this.serviceJwt = serviceJwt;
        this.jwtSupport = validSecret(serviceJwt) ? new ServiceJwtSupport(serviceJwt) : null;
        this.audience = audience;
        this.timeoutMs = timeoutMs;
        this.attempts = Math.max(1, attempts);
    }

    String get(String url, String tenantId, String subjectId, String subjectType, String roles, String permissions, UUID snapshotId, UUID requestId) {
        return send("GET", url, null, tenantId, subjectId, subjectType, roles, permissions, snapshotId, requestId);
    }

    String post(String url, String body, String tenantId, String subjectId, String subjectType, String roles, String permissions, UUID snapshotId, UUID requestId) {
        return send("POST", url, body, tenantId, subjectId, subjectType, roles, permissions, snapshotId, requestId);
    }

    private String send(String method, String url, String body, String tenantId, String subjectId, String subjectType, String roles, String permissions, UUID snapshotId, UUID requestId) {
        if (serviceJwt == null || serviceJwt.isBlank()) throw new CopilotException("service JWT is not configured");
        for (int attempt = 1; attempt <= attempts; attempt++) try {
            URI uri = URI.create(url);
            String token = jwtSupport == null ? serviceJwt : jwtSupport.issue("ai-orchestration-service", audience, List.of(method + ":" + uri.getRawPath()));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs))
                    .header("X-Service-Authorization", "Bearer " + token)
                    .header("X-Tenant-Id", tenantId).header("X-Subject-Id", subjectId).header("X-Subject-Type", subjectType)
                    .header("X-User-Roles", roles == null ? "" : roles).header("X-User-Permissions", permissions == null ? "" : permissions)
                    .header("X-Trace-Id", requestId.toString()).header("X-Request-Id", requestId.toString())
                    .header("Content-Type", "application/json");
            if (snapshotId != null) builder.header("X-Authorization-Snapshot-Id", snapshotId.toString());
            HttpResponse<String> response = client.send("POST".equals(method) ? builder.POST(HttpRequest.BodyPublishers.ofString(body)).build() : builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) return response.body();
            if (response.statusCode() < 500 || attempt == attempts) throw new CopilotException("trusted downstream rejected request");
        } catch (CopilotException exception) { throw exception;
        } catch (Exception exception) { if (attempt == attempts) throw new CopilotException("trusted downstream is unavailable"); }
        throw new CopilotException("trusted downstream is unavailable");
    }
    private static boolean validSecret(String value) { return value != null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32; }
}
