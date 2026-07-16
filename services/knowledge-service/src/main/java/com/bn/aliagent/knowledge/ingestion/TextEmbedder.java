package com.bn.aliagent.knowledge.ingestion;

@FunctionalInterface
public interface TextEmbedder {
    float[] embed(String text);
}
