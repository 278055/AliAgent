package com.bn.aliagent.conversation.core;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
@Profile("legacy-migration")
public class LegacyConversationMigrationCommand {
    @Bean
    CommandLineRunner legacyConversationMigrationRunner(JdbcTemplate target) {
        return args -> {
            String tenantId = required("LEGACY_CONVERSATION_DEFAULT_TENANT_ID");
            String sourceUrl = required("LEGACY_CONVERSATION_SOURCE_URL");
            String sourceUser = required("LEGACY_CONVERSATION_SOURCE_USERNAME");
            String sourcePassword = required("LEGACY_CONVERSATION_SOURCE_PASSWORD");
            JdbcTemplate source = new JdbcTemplate(new DriverManagerDataSource(sourceUrl, sourceUser, sourcePassword));
            List<Map<String, Object>> conversations = source.queryForList("SELECT id, title, pinned, created_at, updated_at FROM conversation");
            for (Map<String, Object> row : conversations) {
                copyConversation(target, tenantId, row);
                copyMessages(source, target, tenantId, UUID.fromString(row.get("id").toString()));
            }
        };
    }

    private void copyConversation(JdbcTemplate target, String tenantId, Map<String, Object> row) {
        UUID id = UUID.fromString(row.get("id").toString());
        if (target.queryForObject("SELECT COUNT(*) FROM legacy_conversation_migration WHERE legacy_conversation_id = ?", Long.class, id) > 0) return;
        Instant created = instant(row.get("created_at"));
        Instant updated = instant(row.get("updated_at"));
        target.update("INSERT INTO conversation (id, tenant_id, owner_subject_id, title, status, pinned, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, "legacy-migration", String.valueOf(row.get("title")), "CLOSED", Boolean.TRUE.equals(row.get("pinned")), Timestamp.from(created), Timestamp.from(updated));
        target.update("INSERT INTO legacy_conversation_migration (legacy_conversation_id, tenant_id, source_name) VALUES (?, ?, ?)", id, tenantId, "postgres");
    }

    private void copyMessages(JdbcTemplate source, JdbcTemplate target, String tenantId, UUID conversationId) {
        for (Map<String, Object> row : source.queryForList("SELECT id, role, content, metadata, created_at FROM message WHERE conversation_id = ? ORDER BY created_at", conversationId.toString())) {
            UUID id = UUID.fromString(row.get("id").toString());
            if (target.queryForObject("SELECT COUNT(*) FROM legacy_message_migration WHERE legacy_message_id = ?", Long.class, id) > 0) continue;
            long sequence = target.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM message WHERE tenant_id = ? AND conversation_id = ?", Long.class, tenantId, conversationId);
            Instant created = instant(row.get("created_at"));
            String senderType = "assistant".equalsIgnoreCase(String.valueOf(row.get("role"))) ? "AI" : "USER";
            target.update("INSERT INTO message (id, tenant_id, conversation_id, sequence, sender_type, message_type, visibility, content, status, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)",
                    id, tenantId, conversationId, sequence, senderType, "TEXT", "PRIVATE", String.valueOf(row.get("content")), "MIGRATED", String.valueOf(row.get("metadata")), Timestamp.from(created), Timestamp.from(created));
            target.update("INSERT INTO legacy_message_migration (legacy_message_id, tenant_id, source_name) VALUES (?, ?, ?)", id, tenantId, "postgres");
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for legacy migration");
        return value;
    }

    private Instant instant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : Instant.now();
    }
}
