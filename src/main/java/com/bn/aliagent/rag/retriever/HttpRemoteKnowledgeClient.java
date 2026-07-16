package com.bn.aliagent.rag.retriever;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 使用 P3-B 的固定 HTTP 契约读取远程知识，不传递 tenantId 参数。 */
public class HttpRemoteKnowledgeClient implements RemoteKnowledgeClient {
    static final String RETRIEVAL_PATH = "/api/v1/knowledge/retrieval:query";
    static final String RETRIEVAL_SCOPE = "POST:/api/v1/knowledge/retrieval:query";
    private static final String NO_GROUNDING_MESSAGE = "没有可用的知识依据";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration timeout;

    public HttpRemoteKnowledgeClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, Duration timeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(baseUrl.replaceAll("/$", "") + RETRIEVAL_PATH);
        this.timeout = timeout;
    }

    @Override
    public RemoteKnowledgeResult retrieve(String query, int topK, TrustedKnowledgeContext context) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("query", query, "topK", topK));
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("X-Tenant-Id", context.tenantId())
                    .header("X-Subject-Id", context.subjectId())
                    .header("X-Subject-Type", context.subjectType())
                    .header("X-User-Roles", context.userRoles())
                    .header("X-User-Permissions", context.userPermissions())
                    .header("X-Authorization-Snapshot-Id", context.authorizationSnapshotId())
                    .header("X-Trace-Id", context.traceId())
                    .header("X-Service-Authorization", context.serviceAuthorization())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return RemoteKnowledgeResult.failure(RemoteFailure.UNAUTHORIZED);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return RemoteKnowledgeResult.failure(RemoteFailure.HTTP_ERROR);
            }
            return parse(response.body());
        } catch (java.net.http.HttpTimeoutException exception) {
            return RemoteKnowledgeResult.failure(RemoteFailure.TIMEOUT);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return RemoteKnowledgeResult.failure(RemoteFailure.HTTP_ERROR);
        } catch (Exception exception) {
            return RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
        }
    }

    private RemoteKnowledgeResult parse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.path("code").canConvertToInt() || root.path("code").asInt() != 200) {
                return RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
            }
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                return RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
            }
            if (items.isEmpty()) {
                return NO_GROUNDING_MESSAGE.equals(root.path("message").asText())
                        ? RemoteKnowledgeResult.noGrounding()
                        : RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
            }
            List<RemoteKnowledgeItem> result = new ArrayList<>();
            for (JsonNode item : items) {
                String documentId = required(item, "documentId");
                String versionId = required(item, "versionId");
                String chunkId = required(item, "chunkId");
                String content = required(item, "content");
                if (!item.path("score").isNumber() || !citationMatches(item.path("citation"), documentId, versionId, chunkId)) {
                    return RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
                }
                result.add(new RemoteKnowledgeItem(documentId, versionId, chunkId, content, (float) item.path("score").asDouble()));
            }
            return RemoteKnowledgeResult.success(result);
        } catch (Exception exception) {
            return RemoteKnowledgeResult.failure(RemoteFailure.PROTOCOL_ERROR);
        }
    }

    private String required(JsonNode item, String field) {
        String value = item.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing remote field: " + field);
        }
        return value;
    }

    private boolean citationMatches(JsonNode citation, String documentId, String versionId, String chunkId) {
        return documentId.equals(citation.path("documentId").asText()) && versionId.equals(citation.path("versionId").asText())
                && chunkId.equals(citation.path("chunkId").asText());
    }
}
