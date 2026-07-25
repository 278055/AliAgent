package com.bn.aliagent.conversation.persistence;

import com.bn.aliagent.conversation.queue.VerifiedConversationTagRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcVerifiedConversationTagRepository implements VerifiedConversationTagRepository {
    private final JdbcTemplate jdbc;
    public JdbcVerifiedConversationTagRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Set<String> verifiedTags(String tenantId, UUID conversationId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT tag_code FROM conversation_verified_tag WHERE tenant_id=? AND conversation_id=? ORDER BY tag_code", String.class, tenantId, conversationId));
    }
    @Override public void verify(String tenantId, UUID conversationId, String tagCode, String source, Instant verifiedAt) {
        jdbc.update("INSERT INTO conversation_verified_tag (tenant_id,conversation_id,tag_code,source,verified_at) VALUES (?,?,?,?,?) ON CONFLICT (tenant_id,conversation_id,tag_code) DO UPDATE SET source=EXCLUDED.source,verified_at=EXCLUDED.verified_at", tenantId, conversationId, tagCode, source, Timestamp.from(verifiedAt));
    }
}
