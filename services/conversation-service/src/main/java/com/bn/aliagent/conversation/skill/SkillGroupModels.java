package com.bn.aliagent.conversation.skill;

import java.util.Set;
import java.util.UUID;

public final class SkillGroupModels {
    private SkillGroupModels() { }

    public record SkillGroup(UUID id, String tenantId, String code, String name, boolean enabled, String dataScope,
                             int maxQueueSize, int assignmentTimeoutSeconds, int maxAssignmentAttempts) {
        public SkillGroup {
            if (id == null || blank(tenantId) || blank(code) || blank(name) || blank(dataScope)) throw new IllegalArgumentException("skill group fields are required");
            if (maxQueueSize < 1 || assignmentTimeoutSeconds < 1 || maxAssignmentAttempts < 1) throw new IllegalArgumentException("skill group limits must be positive");
        }
    }

    public record SkillTag(String tenantId, String code, String name) {
        public SkillTag {
            if (blank(tenantId) || blank(code) || blank(name)) throw new IllegalArgumentException("skill tag fields are required");
        }
    }

    public record RoutingResult(UUID skillGroupId, Set<String> matchedTags, String ruleVersion) { }
    static boolean blank(String value) { return value == null || value.isBlank(); }
}
