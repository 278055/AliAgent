package com.bn.aliagent.knowledge.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnowledgeVersionStateTest {
    @Test
    void 只有已发布版本可被检索契约选中() {
        assertTrue(KnowledgeVersionState.PUBLISHED.isSearchVisible());
        assertFalse(KnowledgeVersionState.DRAFT.isSearchVisible());
        assertFalse(KnowledgeVersionState.PROCESSING.isSearchVisible());
        assertFalse(KnowledgeVersionState.READY_FOR_REVIEW.isSearchVisible());
        assertFalse(KnowledgeVersionState.RETIRED.isSearchVisible());
        assertFalse(KnowledgeVersionState.FAILED.isSearchVisible());
    }
}
