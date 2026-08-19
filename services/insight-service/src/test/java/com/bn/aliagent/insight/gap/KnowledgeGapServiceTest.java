package com.bn.aliagent.insight.gap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class KnowledgeGapServiceTest {
    @Test
    void mergesSignalsWithoutPublishingKnowledge() {
        var service = new KnowledgeGapService();
        service.record("test-tenant", "物流延迟", new GapSignal("handoff", "e1"), "kb-v1");
        var gap = service.record("test-tenant", "物流延迟", new GapSignal("unanswered", "e2"), "kb-v1");

        assertEquals(2, gap.signals().size());
        assertEquals(GapStatus.DRAFT, gap.status());
        assertFalse(gap.knowledgePublished());
    }
}
