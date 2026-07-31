package com.bn.aliagent.insight.topic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicPublicationServiceTest {
    @Test
    void publishesConfidentNormalTopicUsingNameAndSummaryOnly() {
        var service = new TopicPublicationService((tenant, samples, model, prompt) -> new TopicNaming("物流延迟", "多个匿名反馈提及物流延迟"));

        var decision = service.decide(new TopicCandidate("test-tenant", TopicRisk.NORMAL, List.of("m1", "m2"), 8, 0.9), "model-v1", "prompt-v1");

        assertEquals(TopicState.PUBLISHED, decision.state());
        assertEquals("物流延迟", decision.displayName());
        assertEquals(2, decision.memberSnapshot().size());
    }

    @Test
    void highRiskTopicWaitsForSupervisorAndNamingFailureIsPending() {
        var unavailable = new TopicPublicationService((tenant, samples, model, prompt) -> { throw new IllegalStateException("model unavailable"); });

        var highRisk = unavailable.decide(new TopicCandidate("test-tenant", TopicRisk.FRAUD, List.of("m1"), 8, 0.9), "model-v1", "prompt-v1");
        var failed = unavailable.decide(new TopicCandidate("test-tenant", TopicRisk.NORMAL, List.of("m2"), 8, 0.9), "model-v1", "prompt-v1");

        assertEquals(TopicState.PENDING_SUPERVISOR_REVIEW, highRisk.state());
        assertEquals(TopicState.PENDING_NAMING, failed.state());
    }
}
