package com.bn.aliagent.orchestration.copilot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 副驾建议、动作和消费幂等性均以 PostgreSQL 为事实来源。 */
public final class JdbcCopilotRepository implements CopilotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public JdbcCopilotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean claimInbox(UUID eventId) { try { return jdbc.update("INSERT INTO copilot_inbox (event_id,consumer_name,tenant_id,status) VALUES (?,'copilot-suggestion-requested','system','RECEIVED')", eventId) == 1; } catch (DuplicateKeyException duplicate) { return false; } }
    public void completeInbox(UUID eventId) { jdbc.update("UPDATE copilot_inbox SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE event_id=? AND consumer_name='copilot-suggestion-requested'", eventId); }
    public Optional<CopilotModels.Suggestion> findByRequestId(UUID requestId) { return suggestions(" WHERE request_id=?", requestId).stream().findFirst(); }
    public Optional<CopilotModels.Suggestion> findSuggestion(UUID id) { return suggestions(" WHERE id=?", id).stream().findFirst(); }
    public List<CopilotModels.Suggestion> findForConversation(String tenantId, UUID conversationId, String agentId) {
        return suggestions(" WHERE tenant_id=? AND conversation_id=? AND assigned_agent_id=? ORDER BY created_at DESC", tenantId, conversationId, agentId);
    }
    public void save(CopilotModels.Suggestion value, UUID requestId) {
        try {
            jdbc.update("INSERT INTO copilot_suggestion (id,tenant_id,conversation_id,trigger_message_id,assigned_agent_id,refresh_no,visibility,status,original_content,final_content,diff_summary,citations,model_version,prompt_version,workflow_version,request_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?)", value.suggestionId(),value.tenantId(),value.conversationId(),value.triggerMessageId(),value.assignedAgentId(),value.refreshNo(),value.visibility().name(),value.status().name(),value.originalContent(),value.finalContent(),value.diffSummary(),write(value.citations()),value.modelVersion(),value.promptVersion(),value.workflowVersion(),requestId,Timestamp.from(value.createdAt()));
        } catch (DuplicateKeyException duplicate) { }
    }
    public Optional<CopilotModels.Action> findAction(UUID requestId) { return jdbc.query("SELECT request_id,suggestion_id,action_type,original_content,final_content,diff_summary FROM copilot_suggestion_action WHERE request_id=?", (rs,n)->new CopilotModels.Action(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),CopilotModels.ActionType.valueOf(rs.getString(3)),rs.getString(4),rs.getString(5),rs.getString(6)),requestId).stream().findFirst(); }
    public void saveAction(CopilotModels.Action value) { try { jdbc.update("INSERT INTO copilot_suggestion_action (id,tenant_id,suggestion_id,request_id,action_type,original_content,final_content,diff_summary) SELECT ?,tenant_id,?,?,?,?,?,? FROM copilot_suggestion WHERE id=?",UUID.randomUUID(),value.suggestionId(),value.requestId(),value.type().name(),value.originalContent(),value.finalContent(),value.diffSummary(),value.suggestionId()); } catch (DuplicateKeyException duplicate) { } }
    private List<CopilotModels.Suggestion> suggestions(String where,Object...args){return jdbc.query("SELECT id,tenant_id,conversation_id,trigger_message_id,assigned_agent_id,refresh_no,visibility,status,original_content,final_content,diff_summary,model_version,prompt_version,workflow_version,citations,created_at FROM copilot_suggestion"+where,(rs,n)->new CopilotModels.Suggestion(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getInt(6),CopilotModels.Visibility.valueOf(rs.getString(7)),CopilotModels.SuggestionStatus.valueOf(rs.getString(8)),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),read(rs.getString(15)),rs.getTimestamp(16).toInstant()),args);}
    private String write(List<CopilotModels.Citation> value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<CopilotModels.Citation> read(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
}
