package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.skill.SkillGroupAdministrationRepository;
import com.bn.aliagent.conversation.skill.SkillGroupModels;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcSkillGroupAdministrationRepository implements SkillGroupAdministrationRepository {
    private final JdbcTemplate jdbc;
    public JdbcSkillGroupAdministrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public SkillGroupModels.SkillGroup create(String tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_skill_group (id,tenant_id,name,routing_rule_version) VALUES (?,?,?,'p7-routing-v1')", id, tenantId, name);
        return new SkillGroupModels.SkillGroup(id, tenantId, name, name, true, "ALL", 50, 30, 3);
    }
    @Override public void replaceTags(String tenantId, UUID skillGroupId, Set<String> tags) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM agent_skill_group WHERE tenant_id=? AND id=?", Integer.class, tenantId, skillGroupId) != 1) throw new IllegalArgumentException("技能组不存在");
        jdbc.update("DELETE FROM agent_skill_group_tag WHERE tenant_id=? AND skill_group_id=?", tenantId, skillGroupId);
        for (String tag : tags) {
            UUID tagId = jdbc.query("SELECT id FROM agent_skill_tag WHERE tenant_id=? AND code=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, tenantId, tag);
            if (tagId == null) { tagId = UUID.randomUUID(); jdbc.update("INSERT INTO agent_skill_tag (id,tenant_id,code) VALUES (?,?,?)", tagId, tenantId, tag); }
            jdbc.update("INSERT INTO agent_skill_group_tag (tenant_id,skill_group_id,tag_id) VALUES (?,?,?)", tenantId, skillGroupId, tagId);
        }
    }
    @Override public void upsertMember(String tenantId, UUID skillGroupId, String staffId, int maxConcurrent, boolean enabled) {
        jdbc.update("INSERT INTO agent_skill_group_member (tenant_id,skill_group_id,staff_id,max_concurrent,enabled) VALUES (?,?,?,?,?) ON CONFLICT (tenant_id,skill_group_id,staff_id) DO UPDATE SET max_concurrent=EXCLUDED.max_concurrent,enabled=EXCLUDED.enabled,version=agent_skill_group_member.version+1", tenantId, skillGroupId, staffId, maxConcurrent, enabled);
    }
}
