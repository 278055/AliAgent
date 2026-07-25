package com.bn.aliagent.conversation.skill;

import java.util.Set;
import java.util.UUID;

public interface SkillGroupAdministrationRepository {
    SkillGroupModels.SkillGroup create(String tenantId, String name);
    void replaceTags(String tenantId, UUID skillGroupId, Set<String> tags);
    void upsertMember(String tenantId, UUID skillGroupId, String staffId, int maxConcurrent, boolean enabled);
}
