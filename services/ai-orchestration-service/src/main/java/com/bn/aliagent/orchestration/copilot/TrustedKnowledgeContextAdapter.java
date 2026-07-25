package com.bn.aliagent.orchestration.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅使用 Gateway 已签发并随事件传递的授权快照检索知识。 */
public final class TrustedKnowledgeContextAdapter implements CopilotPorts.KnowledgeContextPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedCopilotHttpClient client;

    public TrustedKnowledgeContextAdapter(String baseUrl, String serviceSecret, int timeoutMs, int attempts) {
        this.baseUrl = baseUrl;
        this.client = new TrustedCopilotHttpClient(serviceSecret, "knowledge-service", timeoutMs, attempts);
    }

    @Override public List<CopilotModels.Citation> retrieve(String tenantId, UUID conversationId) {
        throw new CopilotException("authorization snapshot is required for copilot knowledge retrieval");
    }

    @Override public List<CopilotModels.Citation> retrieve(String tenantId, UUID conversationId, UUID snapshotId,
            String subjectId, String subjectType, String roles, String permissions, String query) {
        if (snapshotId == null || blank(subjectId) || blank(subjectType) || blank(roles) || blank(permissions)) {
            throw new CopilotException("authorization snapshot context is required for copilot knowledge retrieval");
        }
        try {
            JsonNode items = JSON.readTree(client.post(baseUrl + "/api/v1/knowledge/retrieval:query",
                    JSON.writeValueAsString(Map.of("query", query, "topK", 5)), tenantId, subjectId, subjectType,
                    roles, permissions, snapshotId, conversationId)).path("data").path("items");
            List<CopilotModels.Citation> values = new ArrayList<>();
            for (JsonNode item : items) values.add(new CopilotModels.Citation(item.path("content").asText(),
                    "knowledge://" + item.path("documentId").asText() + "/" + item.path("chunkId").asText()));
            return values;
        } catch (CopilotException exception) { throw exception;
        } catch (Exception exception) { throw new CopilotException("invalid trusted knowledge response"); }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
