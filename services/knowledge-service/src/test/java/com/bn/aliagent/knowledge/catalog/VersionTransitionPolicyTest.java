package com.bn.aliagent.knowledge.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VersionTransitionPolicyTest {
    @Test
    void 仅允许审核就绪版本发布() {
        assertDoesNotThrow(() -> VersionTransitionPolicy.requirePublishable(KnowledgeVersionState.READY_FOR_REVIEW));
        assertThrows(IllegalStateException.class, () -> VersionTransitionPolicy.requirePublishable(KnowledgeVersionState.PROCESSING));
        assertThrows(IllegalStateException.class, () -> VersionTransitionPolicy.requirePublishable(KnowledgeVersionState.FAILED));
    }
}
