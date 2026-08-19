package com.bn.aliagent.conversation.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class HumanCollaborationOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    public HumanCollaborationOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static HumanCollaborationOutbox noop() { return new HumanCollaborationOutbox(null); }
    public void append(String eventType, String tenantId, UUID conversationId, UUID requestId, String sender, String content, String status) {
        append(eventType, tenantId, conversationId, requestId, sender, content, status, null, null, null, null, null);
    }
    public void append(String eventType, String tenantId, UUID conversationId, UUID requestId, String sender, String content, String status,
            UUID authorizationSnapshotId, String subjectId, String subjectType, String roles, String permissions) {
        if (jdbc == null) return;
        HumanCollaborationEvent event = new HumanCollaborationEvent(UUID.randomUUID(), eventType, tenantId, conversationId, requestId, Instant.now(), sender, content, status, authorizationSnapshotId, subjectId, subjectType, roles, permissions);
        try {
            int version = "copilot.suggestion.requested.v2".equals(eventType) ? 2 : 1;
            String topic = version == 2 ? "copilot.suggestion.requested.v2" : isP9Event(eventType) ? "insight.events.v1" : "conversation.human.events.v1";
            jdbc.update("INSERT INTO human_collaboration_outbox (id,tenant_id,aggregate_id,request_id,topic,event_type,event_version,payload,status) VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),'PENDING') ON CONFLICT (tenant_id,request_id,event_type) DO NOTHING", event.eventId(), tenantId, conversationId, requestId, topic, eventType, version, json.writeValueAsString(event));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("human collaboration event serialization failed", exception); }
    }
    public List<HumanCollaborationEvent> pending(int limit) {
        return jdbc.query("SELECT payload FROM human_collaboration_outbox WHERE status='PENDING' ORDER BY created_at LIMIT ?", (rs, row) -> read(rs.getString(1)), limit);
    }
    public void markPublished(UUID eventId) { jdbc.update("UPDATE human_collaboration_outbox SET status='PUBLISHED' WHERE id=? AND status='PENDING'", eventId); }
    private HumanCollaborationEvent read(String value) { try { return json.readValue(value, HumanCollaborationEvent.class); } catch (JsonProcessingException exception) { throw new IllegalStateException("human collaboration event payload is invalid", exception); } }
    private static boolean isP9Event(String eventType) { return "conversation.completed".equals(eventType) || "conversation.feedback.received".equals(eventType) || "conversation.human.requested".equals(eventType) || "conversation.human.first-public-reply".equals(eventType); }
}
