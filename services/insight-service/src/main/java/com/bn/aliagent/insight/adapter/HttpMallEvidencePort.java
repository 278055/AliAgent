package com.bn.aliagent.insight.adapter;

import com.bn.aliagent.insight.adapter.MallEvidenceVerificationClient.MallEvidencePort;
import com.bn.aliagent.insight.adapter.MallEvidenceVerificationClient.VerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 订单事实不落地，核验响应只在当前请求内解析并返回。 */
public final class HttpMallEvidencePort implements MallEvidencePort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final HttpClient client;

    public HttpMallEvidencePort(String baseUrl) { this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()); }
    HttpMallEvidencePort(String baseUrl, HttpClient client) { this.baseUrl = baseUrl; this.client = client; }

    @Override
    public VerificationResult verify(MallEvidenceVerificationClient.Request verification, MallEvidenceVerificationClient.Supervisor supervisor) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/v1/insights/evidence/" + verification.evidenceRef()))
                    .timeout(Duration.ofSeconds(3)).header("X-Service-Authorization", "Bearer " + verification.serviceJwt())
                    .header("X-Tenant-Id", verification.tenantId()).header("X-Subject-Id", supervisor.subjectId())
                    .header("X-Subject-Type", "STAFF").header("X-User-Roles", String.join(",", supervisor.roles())).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("mall 核验拒绝，状态=" + response.statusCode());
            return JSON.readValue(response.body(), VerificationResult.class);
        } catch (Exception exception) {
            throw new IllegalStateException("mall 核验不可用", exception);
        }
    }
}
