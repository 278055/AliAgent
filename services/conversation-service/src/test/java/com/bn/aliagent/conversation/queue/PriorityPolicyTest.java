package com.bn.aliagent.conversation.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PriorityPolicyTest {
    @Test
    void 高风险售后必须高于普通会员且固定规则版本() {
        var policy = new HumanQueueModels.PriorityPolicy();
        var risky = policy.score(new HumanQueueModels.PriorityInput(true, false, "NORMAL", Duration.ZERO));
        var vip = policy.score(new HumanQueueModels.PriorityInput(false, false, "VIP", Duration.ofMinutes(30)));

        assertTrue(risky.score() > vip.score());
        assertEquals("p7-priority-v1", risky.ruleVersion());
    }
}
