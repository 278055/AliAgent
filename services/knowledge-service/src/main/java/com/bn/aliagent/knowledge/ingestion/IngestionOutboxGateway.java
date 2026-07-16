package com.bn.aliagent.knowledge.ingestion;

import java.util.List;

public interface IngestionOutboxGateway {
    void enqueue(IngestionTaskMessage message);
    List<IngestionTaskMessage> pending(int limit);
    void markPublished(String eventId);
}
