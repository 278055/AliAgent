package com.bn.aliagent.conversation.skill;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.agent.AgentModels;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillGroupModelsTest {
    @Test
    void 成员容量必须为正且租户不能为空() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentModels.Membership("", UUID.randomUUID(), "staff-1", true, 0));
    }
}
