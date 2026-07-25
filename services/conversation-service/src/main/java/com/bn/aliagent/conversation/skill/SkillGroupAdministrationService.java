package com.bn.aliagent.conversation.skill;

import java.util.Set;
import java.util.UUID;

public final class SkillGroupAdministrationService {
    private final SkillGroupAdministrationRepository repository;
    public SkillGroupAdministrationService(SkillGroupAdministrationRepository repository) { this.repository = repository; }
    public SkillGroupModels.SkillGroup create(String tenantId, String name) {
        if (blank(tenantId) || blank(name)) throw new IllegalArgumentException("技能组名称不能为空");
        return repository.create(tenantId, name);
    }
    public void replaceTags(String tenantId, UUID skillGroupId, Set<String> tags) {
        if (blank(tenantId) || skillGroupId == null || tags == null || tags.isEmpty() || tags.stream().anyMatch(this::blank)) throw new IllegalArgumentException("技能组标签不能为空");
        repository.replaceTags(tenantId, skillGroupId, Set.copyOf(tags));
    }
    public void upsertMember(String tenantId, UUID skillGroupId, String staffId, int maxConcurrent, boolean enabled) {
        if (blank(tenantId) || skillGroupId == null || blank(staffId) || maxConcurrent < 1) throw new IllegalArgumentException("技能组成员参数无效");
        repository.upsertMember(tenantId, skillGroupId, staffId, maxConcurrent, enabled);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
