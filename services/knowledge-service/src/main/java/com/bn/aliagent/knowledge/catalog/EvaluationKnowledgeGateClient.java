package com.bn.aliagent.knowledge.catalog;

import com.bn.platform.security.ServiceJwtSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 通过评估服务验证持久化 Gate 证明；任何不确定结果均拒绝发布。 */
final class EvaluationKnowledgeGateClient implements KnowledgeGateDecisionPort {
    private static final String PATH = "/internal/api/v1/evaluation/gate-proofs:verify";
    private final String baseUrl;
    private final String serviceJwtSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final ObjectMapper json = new ObjectMapper();

    EvaluationKnowledgeGateClient(String baseUrl, String serviceJwtSecret) {
        this.baseUrl = baseUrl;
        this.serviceJwtSecret = serviceJwtSecret;
    }

    @Override
    public void requirePass(String tenantId, UUID versionId, String manifestDigest, String policyVersion, String proof) {
        try {
            JsonNode parsedProof = json.readTree(proof);
            JsonNode requestBody = json.createObjectNode().put("tenantId", tenantId).put("artifactType", "KNOWLEDGE")
                    .put("artifactVersionId", versionId.toString()).put("manifestDigest", manifestDigest).put("policyVersion", policyVersion)
                    .set("proof", parsedProof);
            String token = new ServiceJwtSupport(serviceJwtSecret).issue("knowledge-service", "evaluation-service", List.of("POST:" + PATH));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH)).timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json").header("X-Tenant-Id", tenantId)
                    .header("X-Service-Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode accepted = response.statusCode() / 100 == 2 ? json.readTree(response.body()).path("data").get("accepted") : null;
            if (accepted == null || !accepted.isBoolean() || !accepted.booleanValue()) throw new SecurityException("Gate Decision 未通过验证");
        } catch (SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SecurityException("Gate Decision 验证不可用", exception);
        }
    }
}
