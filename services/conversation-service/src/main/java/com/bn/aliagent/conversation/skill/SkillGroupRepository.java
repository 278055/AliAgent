package com.bn.aliagent.conversation.skill;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillGroupRepository {
    Optional<SkillGroupModels.SkillGroup> find(String tenantId, UUID skillGroupId);
    List<SkillGroupModels.SkillGroup> enabledForTags(String tenantId, java.util.Set<String> verifiedTags);
}
