package com.bn.aliagent.knowledge.ingestion;

@FunctionalInterface
public interface IngestionMessagePublisher {
    void publish(IngestionTaskMessage message);
}
