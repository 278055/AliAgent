package com.bn.aliagent.knowledge.catalog;

public final class EmbeddingValidator {
    public static final int DIMENSION = 1024;

    private EmbeddingValidator() {
    }

    public static void requireDimension(float[] embedding) {
        if (embedding == null || embedding.length != DIMENSION) {
            throw new IllegalArgumentException("嵌入向量必须为 1024 维");
        }
    }
}
