package com.bn.aliagent.knowledge.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmbeddingValidatorTest {
    @Test
    void 仅接受1024维向量() {
        assertDoesNotThrow(() -> EmbeddingValidator.requireDimension(new float[1024]));
        assertThrows(IllegalArgumentException.class, () -> EmbeddingValidator.requireDimension(new float[1023]));
    }
}
