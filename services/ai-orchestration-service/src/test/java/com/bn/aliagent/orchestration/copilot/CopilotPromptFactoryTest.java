package com.bn.aliagent.orchestration.copilot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotPromptFactoryTest {
    @Test
    void promptForbidsHallucinatedFactsAndChainOfThought() {
        var prompt = new CopilotPromptFactory().create(new CopilotModels.PromptContext(
                new CopilotPorts.ConversationContext("test-tenant", UUID.randomUUID(), "HUMAN_ACTIVE", "staff-1", List.of("客户消息")),
                List.of(new CopilotModels.Citation("规则", "kb://rule")), List.of("订单已支付"), List.of("无售后")));

        assertTrue(prompt.contains("不得虚构订单、物流、退款或审批事实"));
        assertTrue(prompt.contains("不要输出思维过程"));
    }
}
