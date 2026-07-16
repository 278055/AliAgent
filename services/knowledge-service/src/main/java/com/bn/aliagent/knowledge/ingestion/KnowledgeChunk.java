package com.bn.aliagent.knowledge.ingestion;

public record KnowledgeChunk(int sequence, String content, float[] embedding) {
}
