package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.skill.SkillGroupModels;
import com.bn.aliagent.conversation.skill.SkillGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 技能组路由仅匹配服务端已验证的标签编码。 */
public final class JdbcSkillGroupRepository implements SkillGroupRepository {
    private final JdbcTemplate jdbc;

    public JdbcSkillGroupRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<SkillGroupModels.SkillGroup> find(String tenantId, UUID skillGroupId) {
        return groups(" WHERE tenant_id = ? AND id = ?", tenantId, skillGroupId).stream().findFirst();
    }

    @Override
    public List<SkillGroupModels.SkillGroup> enabledForTags(String tenantId, Set<String> verifiedTags) {
        if (verifiedTags == null || verifiedTags.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(verifiedTags.size(), "?"));
        Object[] arguments = new Object[verifiedTags.size() + 1];
        arguments[0] = tenantId;
        int index = 1;
        for (String tag : verifiedTags) arguments[index++] = tag;
        return jdbc.query("SELECT DISTINCT g.id,g.tenant_id,g.name,g.name,g.enabled,'ALL',50,30,g.max_assignment_attempts "
                        + "FROM agent_skill_group g JOIN agent_skill_group_tag gt ON gt.tenant_id=g.tenant_id AND gt.skill_group_id=g.id "
                        + "JOIN agent_skill_tag tag ON tag.id=gt.tag_id AND tag.tenant_id=g.tenant_id "
                        + "WHERE g.tenant_id=? AND g.enabled AND tag.code IN (" + placeholders + ") ORDER BY g.name,g.id",
                (rs, row) -> map(rs), arguments);
    }

    private List<SkillGroupModels.SkillGroup> groups(String where, Object... arguments) {
        return jdbc.query("SELECT id,tenant_id,name,name,enabled,'ALL',50,30,max_assignment_attempts FROM agent_skill_group" + where,
                (rs, row) -> map(rs), arguments);
    }

    private SkillGroupModels.SkillGroup map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SkillGroupModels.SkillGroup(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBoolean(5), rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9));
    }
}
