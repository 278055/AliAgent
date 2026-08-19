package com.bn.aliagent.knowledge.insight;

import java.util.List;
import java.util.UUID;

public interface KnowledgeInsightOutbox {
    void append(String tenantId, String traceId, UUID versionId, String coverageVersion);
    List<KnowledgeInsightEvent> pending(int limit);
    void markPublished(UUID eventId);
}
