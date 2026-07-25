package com.bn.aliagent.conversation.skill;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillGroupAdministrationServiceTest {
    @Test
    void 技能组标签和成员必须由主管管理而非客户端入队请求设置() {
        SkillGroupAdministrationRepository repository = new SkillGroupAdministrationRepository() {
            public SkillGroupModels.SkillGroup create(String tenant, String name) { return null; }
            public void replaceTags(String tenant, UUID group, Set<String> tags) { }
            public void upsertMember(String tenant, UUID group, String staff, int capacity, boolean enabled) { }
        };
        SkillGroupAdministrationService service = new SkillGroupAdministrationService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.replaceTags("tenant-a", UUID.randomUUID(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.upsertMember("tenant-a", UUID.randomUUID(), "", 1, true));
    }
}
