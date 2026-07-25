package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.agent.AgentDirectoryPort;
import com.bn.aliagent.conversation.agent.AgentModels;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 队列和客服目录的 PostgreSQL 适配器。 */
public final class JdbcHumanAgentAdapters implements HumanQueueRepository, AgentDirectoryPort {
    private final JdbcTemplate jdbc;

    public JdbcHumanAgentAdapters(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<HumanQueueModels.QueueItem> findByRequest(String tenantId, UUID requestId) { return queue(" WHERE tenant_id = ? AND request_id = ?", tenantId, requestId).stream().findFirst(); }
    @Override public Optional<HumanQueueModels.QueueItem> findActive(String tenantId, UUID conversationId) { return queue(" WHERE tenant_id = ? AND conversation_id = ? AND status IN ('WAITING','OFFERED','CLAIMABLE')", tenantId, conversationId).stream().findFirst(); }
    @Override public Optional<HumanQueueModels.QueueItem> find(String tenantId, UUID queueItemId) { return queue(" WHERE tenant_id = ? AND id = ?", tenantId, queueItemId).stream().findFirst(); }
    @Override public Optional<HumanQueueModels.QueueItem> find(UUID queueItemId) { return queue(" WHERE id = ?", queueItemId).stream().findFirst(); }

    @Override
    public HumanQueueModels.QueueItem createIfAbsent(HumanQueueModels.QueueItem item) {
        try {
            jdbc.update("INSERT INTO human_queue_item (id, tenant_id, conversation_id, skill_group_id, priority, routing_rule_version, priority_rule_version, request_id, assignment_attempts, status, enqueued_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    item.id(), item.tenantId(), item.conversationId(), item.skillGroupId(), item.priority(), item.routingRuleVersion(), item.priorityRuleVersion(), item.requestId(), item.assignmentAttempts(), item.status().name(), Timestamp.from(item.enqueuedAt()));
            return item;
        } catch (DuplicateKeyException ignored) {
            return findByRequest(item.tenantId(), item.requestId()).or(() -> findActive(item.tenantId(), item.conversationId())).orElseThrow(() -> ignored);
        }
    }

    @Override public void save(HumanQueueModels.QueueItem item) { jdbc.update("UPDATE human_queue_item SET assignment_attempts = ?, status = ?, version = version + 1 WHERE id = ? AND tenant_id = ?", item.assignmentAttempts(), item.status().name(), item.id(), item.tenantId()); }
    @Override public List<HumanQueueModels.QueueItem> list(String tenantId) { return queue(" WHERE tenant_id = ? ORDER BY priority DESC, enqueued_at", tenantId); }

    @Override
    public List<AgentModels.AgentSnapshot> candidates(String tenantId, UUID skillGroupId) {
        return jdbc.query("SELECT m.tenant_id, m.skill_group_id, m.staff_id, COALESCE(p.status, 'OFFLINE'), m.enabled, m.max_concurrent, COUNT(t.id), m.last_assigned_at FROM agent_skill_group_member m JOIN conversation_agent_presence p ON p.tenant_id = m.tenant_id AND p.staff_id = m.staff_id AND p.status = 'ONLINE' LEFT JOIN human_takeover t ON t.tenant_id = m.tenant_id AND t.staff_id = m.staff_id AND t.status = 'ACTIVE' WHERE m.tenant_id = ? AND m.skill_group_id = ? AND m.enabled GROUP BY m.tenant_id, m.skill_group_id, m.staff_id, p.status, m.enabled, m.max_concurrent, m.last_assigned_at HAVING COUNT(t.id) < m.max_concurrent",
                (rs, row) -> new AgentModels.AgentSnapshot(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), AgentModels.Presence.valueOf(rs.getString(4)), rs.getBoolean(5), rs.getInt(6), rs.getInt(7), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()), tenantId, skillGroupId);
    }

    private List<HumanQueueModels.QueueItem> queue(String where, Object... args) {
        return jdbc.query("SELECT id, tenant_id, conversation_id, skill_group_id, priority, routing_rule_version, priority_rule_version, enqueued_at, request_id, assignment_attempts, status FROM human_queue_item" + where,
                (rs, row) -> new HumanQueueModels.QueueItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getObject(9, UUID.class), rs.getInt(10), HumanQueueModels.QueueStatus.valueOf(rs.getString(11))), args);
    }
}
