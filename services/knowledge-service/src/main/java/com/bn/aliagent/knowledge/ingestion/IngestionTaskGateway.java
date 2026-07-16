package com.bn.aliagent.knowledge.ingestion;

import java.util.List;

public interface IngestionTaskGateway {
    boolean registerConsumption(String eventId, String consumer);
    IngestionSource load(String taskId, String tenantId);
    void replaceChunks(String versionId, String tenantId, List<KnowledgeChunk> values);
    void markReadyForReview(String taskId, String tenantId);
    void markFailed(String taskId, String tenantId, String diagnostic);
}
