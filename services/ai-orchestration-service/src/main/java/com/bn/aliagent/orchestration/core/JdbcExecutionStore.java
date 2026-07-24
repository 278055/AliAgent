package com.bn.aliagent.orchestration.core;

import com.bn.aliagent.orchestration.routing.Intent;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 以迁移中已有的执行和 Inbox 表实现跨进程的幂等与恢复。 */
public final class JdbcExecutionStore implements ExecutionStore {
    private final JdbcTemplate jdbc;

    public JdbcExecutionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean claimInbox(UUID eventId, String consumer, String tenantId) {
        return jdbc.update("INSERT INTO orchestration_inbox(event_id, consumer_name, tenant_id, status) VALUES (?, ?, ?, 'PROCESSING') ON CONFLICT (event_id, consumer_name) DO NOTHING",
                eventId, consumer, tenantId) == 1;
    }
    @Override public void completeInbox(UUID eventId, String consumer) {
        jdbc.update("UPDATE orchestration_inbox SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE event_id = ? AND consumer_name = ?", eventId, consumer);
    }
    @Override public boolean create(ExecutionRecord record) {
        ReplyRequestedV2 value = record.request();
        boolean created = jdbc.update("INSERT INTO orchestration_execution(id, event_id, tenant_id, conversation_id, message_id, generation_id, request_id, trace_id, subject_id, subject_type, authorization_snapshot_id, intent_type, workflow_type, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                record.executionId(), value.eventId(), value.tenantId(), value.conversationId(), value.messageId(), value.generationId(), value.requestId(), value.traceId(), "unavailable", "UNAVAILABLE", value.eventId(), record.intent().name(), record.intent().name(), record.status().name()) == 1;
        if (created) jdbc.update("INSERT INTO orchestration_state_transition(execution_id, to_status, reason) VALUES (?, 'CREATED', ?)", record.executionId(), "replyMessageId:" + value.replyMessageId());
        return created;
    }
    @Override public Optional<ExecutionRecord> find(UUID executionId) {
        return jdbc.query(selectSql() + " WHERE id = ?", (rs, row) -> record(rs), executionId).stream().findFirst();
    }
    @Override public Optional<ExecutionRecord> findByRequestId(UUID requestId) {
        return jdbc.query(selectSql() + " WHERE request_id = ?", (rs, row) -> record(rs), requestId).stream().findFirst();
    }
    @Override public void updateStatus(UUID executionId, ExecutionStateMachine.Status status) {
        jdbc.update("UPDATE orchestration_execution SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status.name(), executionId);
    }
    @Override public boolean isStepCompleted(UUID executionId, String step) {
        if (!"MODEL".equals(step)) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM model_invocation_audit WHERE execution_id = ? AND outcome = 'COMPLETED'", Integer.class, executionId);
        return count != null && count > 0;
    }
    @Override public void markStepCompleted(UUID executionId, String step) {
        if ("MODEL".equals(step)) jdbc.update("INSERT INTO model_invocation_audit(id, execution_id, tenant_id, outcome, token_usage) SELECT ?, id, tenant_id, 'COMPLETED', '{}'::jsonb FROM orchestration_execution WHERE id = ?", UUID.randomUUID(), executionId);
    }
    @Override public List<ExecutionRecord> findRecoverable() {
        return jdbc.query(selectSql() + " WHERE status IN ('CREATED', 'ROUTING', 'RUNNING', 'RETRY_PENDING')", (rs, row) -> record(rs));
    }

    private String selectSql() {
        return "SELECT id, event_id, tenant_id, conversation_id, message_id, generation_id, request_id, trace_id, intent_type, status, created_at, "
                + "(SELECT substring(reason FROM 16) FROM orchestration_state_transition WHERE execution_id = orchestration_execution.id AND reason LIKE 'replyMessageId:%' ORDER BY id LIMIT 1) AS reply_message_id FROM orchestration_execution";
    }

    private ExecutionRecord record(ResultSet rs) throws java.sql.SQLException {
        ReplyRequestedV2 request = new ReplyRequestedV2(rs.getObject("event_id", UUID.class), rs.getString("tenant_id"), rs.getObject("conversation_id", UUID.class), rs.getObject("message_id", UUID.class), UUID.fromString(rs.getString("reply_message_id")), rs.getObject("generation_id", UUID.class), rs.getObject("request_id", UUID.class), rs.getString("trace_id"), rs.getTimestamp("created_at").toInstant());
        return new ExecutionRecord(rs.getObject("id", UUID.class), request, Intent.valueOf(rs.getString("intent_type")), ExecutionStateMachine.Status.valueOf(rs.getString("status")));
    }
}
