package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.KnowledgeRetrievalPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KnowledgeServiceRetrievalAdapter implements KnowledgeRetrievalPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedHttpClient client;
    public KnowledgeServiceRetrievalAdapter(String baseUrl, String jwt, int timeoutMs, int attempts) { this.baseUrl = baseUrl; this.client = new TrustedHttpClient(jwt, timeoutMs, attempts); }
    @Override public List<OrchestrationContract.Citation> retrieve(OrchestrationContract.ExecutionContext context, String query, int topK) {
        if (query == null || query.isBlank() || topK < 1 || topK > 20) throw new AdapterException(AdapterException.Category.VALIDATION, "invalid knowledge retrieval schema");
        try {
            JsonNode items = JSON.readTree(client.post(baseUrl + "/api/v1/knowledge/retrieval:query", JSON.writeValueAsString(java.util.Map.of("query", query, "topK", topK)), context)).path("data").path("items");
            List<OrchestrationContract.Citation> citations = new ArrayList<>();
            for (JsonNode item : items) citations.add(new OrchestrationContract.Citation(UUID.fromString(item.path("documentId").asText()), UUID.fromString(item.path("versionId").asText()), UUID.fromString(item.path("chunkId").asText()), item.path("content").asText(), item.path("score").asDouble()));
            return citations;
        } catch (AdapterException exception) { throw exception;
        } catch (Exception exception) { throw new AdapterException(AdapterException.Category.REMOTE, "invalid knowledge response", exception); }
    }
}
