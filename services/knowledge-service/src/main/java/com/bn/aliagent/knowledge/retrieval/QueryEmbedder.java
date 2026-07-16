package com.bn.aliagent.knowledge.retrieval;

@FunctionalInterface
public interface QueryEmbedder {
    float[] embed(String query);
}
