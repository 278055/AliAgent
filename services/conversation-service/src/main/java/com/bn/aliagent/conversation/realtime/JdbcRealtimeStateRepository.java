package com.bn.aliagent.conversation.realtime;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public final class JdbcRealtimeStateRepository implements RealtimeStateRepository {
    private final JdbcTemplate jdbc;
    private final int ttlSeconds;
    public JdbcRealtimeStateRepository(JdbcTemplate jdbc) { this(jdbc, 60); }
    JdbcRealtimeStateRepository(JdbcTemplate jdbc, int ttlSeconds) { this.jdbc = jdbc; this.ttlSeconds = ttlSeconds; }
    @Override public void saveConnection(RealtimeConnection value) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO conversation_realtime_connection (connection_id, tenant_id, conversation_id, instance_id, connected_at, expires_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (connection_id) DO UPDATE SET instance_id = EXCLUDED.instance_id, expires_at = EXCLUDED.expires_at",
                value.connectionId(), value.tenantId(), value.conversationId(), value.instanceId(), Timestamp.from(now), Timestamp.from(now.plusSeconds(ttlSeconds)));
    }
    @Override public void heartbeat(RealtimeConnection value) { jdbc.update("UPDATE conversation_realtime_connection SET expires_at = ? WHERE connection_id = ? AND tenant_id = ?", Timestamp.from(Instant.now().plusSeconds(ttlSeconds)), value.connectionId(), value.tenantId()); }
    @Override public void deleteConnection(String tenantId, UUID connectionId) { jdbc.update("DELETE FROM conversation_realtime_connection WHERE tenant_id = ? AND connection_id = ?", tenantId, connectionId); }
    @Override public void savePresence(String tenantId, String staffId, String status) { jdbc.update("INSERT INTO conversation_agent_presence (tenant_id, staff_id, status, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (tenant_id, staff_id) DO UPDATE SET status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP", tenantId, staffId, status); }
    @Override public void saveHumanState(String tenantId, UUID conversationId, String staffId, String status) { jdbc.update("INSERT INTO conversation_human_state (tenant_id, conversation_id, staff_id, status, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT (tenant_id, conversation_id) DO UPDATE SET staff_id = EXCLUDED.staff_id, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP", tenantId, conversationId, staffId, status); }
}
