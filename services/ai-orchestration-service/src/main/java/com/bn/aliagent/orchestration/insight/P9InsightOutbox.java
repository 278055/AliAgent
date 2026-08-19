package com.bn.aliagent.orchestration.insight;

import java.util.List;
import java.util.UUID;

public interface P9InsightOutbox {
    void append(String tenantId, String traceId, UUID executionId, String eventType);
    List<P9InsightEvent> pending(int limit);
    void markPublished(UUID eventId);
}
