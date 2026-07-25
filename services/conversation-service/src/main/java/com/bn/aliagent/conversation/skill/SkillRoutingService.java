package com.bn.aliagent.conversation.skill;

import java.util.Comparator;
import java.util.Set;

public final class SkillRoutingService {
    public static final String RULE_VERSION = "p7-routing-v1";
    private final SkillGroupRepository groups;

    public SkillRoutingService(SkillGroupRepository groups) { this.groups = groups; }

    public SkillGroupModels.RoutingResult route(String tenantId, Set<String> verifiedTags) {
        Set<String> tags = verifiedTags == null ? Set.of() : Set.copyOf(verifiedTags);
        var group = groups.enabledForTags(tenantId, tags).stream().min(Comparator.comparing(SkillGroupModels.SkillGroup::code))
                .orElseThrow(() -> new IllegalArgumentException("no enabled skill group matches verified tags"));
        return new SkillGroupModels.RoutingResult(group.id(), tags, RULE_VERSION);
    }
}
