package com.bn.aliagent.insight.topic;

public interface EmbeddingPort {
    EmbeddingVector embed(String tenantId, String anonymizedText, String modelVersion);
}
