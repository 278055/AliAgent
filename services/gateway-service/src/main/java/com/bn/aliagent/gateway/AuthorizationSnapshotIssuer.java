package com.bn.aliagent.gateway;

import com.bn.platform.security.ServiceJwtSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

final class AuthorizationSnapshotIssuer implements AuthorizationSnapshotPort {
    private final String baseUrl;
    private final ServiceJwtSupport jwt;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final ObjectMapper json = new ObjectMapper();
    AuthorizationSnapshotIssuer(String baseUrl, String secret) { this.baseUrl = baseUrl; this.jwt = new ServiceJwtSupport(secret); }
    public String issue(TrustedIdentity identity, String traceId, String requestId) {
        try {
            String path = "/internal/api/v1/authorization-snapshots";
            String token = jwt.issue("gateway-service", "knowledge-service", List.of("POST:" + path));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(2))
                    .header("X-Service-Authorization", "Bearer " + token).header("X-Tenant-Id", identity.tenantId())
                    .header("X-Subject-Id", identity.subjectId()).header("X-Subject-Type", identity.subjectType())
                    .header("X-User-Roles", String.join(",", identity.roles())).header("X-User-Permissions", String.join(",", identity.permissions()))
                    .header("X-Trace-Id", traceId).header("X-Request-Id", requestId).POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("knowledge authorization snapshot rejected");
            return json.readTree(response.body()).path("id").asText(null);
        } catch (Exception exception) { throw new IllegalStateException("knowledge authorization snapshot unavailable", exception); }
    }
}
