package com.bn.aliagent.conversation.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrustedSkillRoutingServiceTest {
    @Test
    void 只按服务端已验证标签选择技能组() {
        UUID groupId = UUID.randomUUID();
        SkillGroupRepository groups = new SkillGroupRepository() {
            public Optional<SkillGroupModels.SkillGroup> find(String tenantId, UUID id) { return Optional.empty(); }
            public List<SkillGroupModels.SkillGroup> enabledForTags(String tenantId, Set<String> tags) {
                return tags.equals(Set.of("AFTERSALE_VERIFIED"))
                        ? List.of(new SkillGroupModels.SkillGroup(groupId, tenantId, "after-sale", "售后", true, "ALL", 50, 30, 3)) : List.of();
            }
        };

        assertEquals(groupId, new SkillRoutingService(groups).route("tenant-a", Set.of("AFTERSALE_VERIFIED")).skillGroupId());
        assertThrows(IllegalArgumentException.class, () -> new SkillRoutingService(groups).route("tenant-a", Set.of("客户端声称的标签")));
    }
}
