package com.bn.aliagent.orchestration.resilience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SafeDegradationPolicyTest {
    @Test
    void modelFailureRequestsHumanHandoff() {
        assertEquals(DegradationDecision.HUMAN_HANDOFF, new SafeDegradationPolicy(false).decide(DependencyType.MODEL));
    }

    @Test
    void knowledgeAndMallFailuresBlockUnsupportedFacts() {
        SafeDegradationPolicy policy = new SafeDegradationPolicy(true);
        assertEquals(DegradationDecision.NO_UNSUPPORTED_POLICY, policy.decide(DependencyType.KNOWLEDGE));
        assertEquals(DegradationDecision.NO_UNSUPPORTED_MALL_FACTS, policy.decide(DependencyType.MALL));
    }
}
