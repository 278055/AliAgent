package com.bn.aliagent.orchestration.copilot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCopilotRepository implements CopilotRepository {
    private final JdbcTemplate jdbc;
    public JdbcCopilotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public boolean claimInbox(UUID eventId) {
        return jdbc.update("INSERT INTO copilot_inbox(event_id, consumer_name, tenant_id, status) VALUES (?, 'copilot-suggestion', 'pending', 'RECEIVED') ON CONFLICT DO NOTHING", eventId) == 1;
    }
    @Override public void completeInbox(UUID eventId) { jdbc.update("UPDATE copilot_inbox SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP WHERE event_id=? AND consumer_name='copilot-suggestion'", eventId); }
    @Override public Optional<CopilotModels.Suggestion> findByRequestId(UUID requestId) { return find("SELECT * FROM copilot_suggestion WHERE request_id=?", requestId); }
    @Override public Optional<CopilotModels.Suggestion> findSuggestion(UUID suggestionId) { return find("SELECT * FROM copilot_suggestion WHERE id=?", suggestionId); }
    @Override public void save(CopilotModels.Suggestion value, UUID requestId) {
        jdbc.update("INSERT INTO copilot_suggestion(id,tenant_id,conversation_id,trigger_message_id,assigned_agent_id,refresh_no,visibility,status,original_content,final_content,diff_summary,citations,model_version,prompt_version,workflow_version,request_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?) ON CONFLICT (tenant_id,request_id) DO UPDATE SET status=EXCLUDED.status, final_content=EXCLUDED.final_content, diff_summary=EXCLUDED.diff_summary",
                value.suggestionId(), value.tenantId(), value.conversationId(), value.triggerMessageId(), value.assignedAgentId(), value.refreshNo(), value.visibility().name(), value.status().name(), value.originalContent(), value.finalContent(), value.diffSummary(), "[]", value.modelVersion(), value.promptVersion(), value.workflowVersion(), requestId, Timestamp.from(value.createdAt()));
    }
    @Override public Optional<CopilotModels.Action> findAction(UUID requestId) {
        List<CopilotModels.Action> values = jdbc.query("SELECT request_id,suggestion_id,action_type,original_content,final_content,diff_summary FROM copilot_suggestion_action WHERE request_id=?", (rs, row) -> new CopilotModels.Action(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), CopilotModels.ActionType.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5), rs.getString(6)), requestId);
        return values.stream().findFirst();
    }
    @Override public void saveAction(CopilotModels.Action value) {
        jdbc.update("INSERT INTO copilot_suggestion_action(id,tenant_id,suggestion_id,request_id,action_type,original_content,final_content,diff_summary) SELECT ?,tenant_id,?,?,?,?,?,? FROM copilot_suggestion WHERE id=? ON CONFLICT (tenant_id,request_id) DO NOTHING", UUID.randomUUID(), value.suggestionId(), value.requestId(), value.type().name(), value.originalContent(), value.finalContent(), value.diffSummary(), value.suggestionId());
    }
    private Optional<CopilotModels.Suggestion> find(String sql, UUID id) {
        List<CopilotModels.Suggestion> values = jdbc.query(sql, (rs, row) -> new CopilotModels.Suggestion(UUID.fromString(rs.getString("id")), rs.getString("tenant_id"), UUID.fromString(rs.getString("conversation_id")), UUID.fromString(rs.getString("trigger_message_id")), rs.getString("assigned_agent_id"), rs.getInt("refresh_no"), CopilotModels.Visibility.valueOf(rs.getString("visibility")), CopilotModels.SuggestionStatus.valueOf(rs.getString("status")), rs.getString("original_content"), rs.getString("final_content"), rs.getString("diff_summary"), rs.getString("model_version"), rs.getString("prompt_version"), rs.getString("workflow_version"), List.of(), rs.getTimestamp("created_at").toInstant()), id);
        return values.stream().findFirst();
    }
}
