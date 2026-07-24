package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/internal/api/v1/conversations")
public class InternalCopilotContextController {
    private final JdbcTemplate jdbc;
    public InternalCopilotContextController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{conversationId}/copilot-context")
    public Map<String, Object> context(@PathVariable UUID conversationId, HttpServletRequest request) {
        var trusted = TrustedConversationRequestContext.from(request);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.tenant_id, c.status AS conversation_status, COALESCE(h.status, c.status) AS collaboration_status, h.staff_id FROM conversation c LEFT JOIN conversation_human_state h ON h.tenant_id=c.tenant_id AND h.conversation_id=c.id WHERE c.id=? AND c.tenant_id=? AND c.deleted_at IS NULL", conversationId, trusted.tenantId());
        if (rows.isEmpty()) throw new IllegalArgumentException("conversation is not accessible");
        Map<String, Object> row = rows.get(0);
        List<String> messages = jdbc.query("SELECT content FROM message WHERE tenant_id=? AND conversation_id=? AND visibility='PUBLIC' ORDER BY sequence", (rs, index) -> rs.getString(1), trusted.tenantId(), conversationId);
        return Map.of("tenantId", trusted.tenantId(), "conversationId", conversationId, "collaborationState", row.get("collaboration_status"), "assignedAgentId", row.get("staff_id") == null ? "" : row.get("staff_id"), "messages", messages);
    }
}
