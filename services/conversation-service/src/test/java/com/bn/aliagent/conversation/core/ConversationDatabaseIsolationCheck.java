package com.bn.aliagent.conversation.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Run manually with the local PostgreSQL JDBC driver after explicit authorization. */
public final class ConversationDatabaseIsolationCheck {
    public static void main(String[] args) throws Exception {
        String adminUrl = "jdbc:postgresql://localhost:5432/postgres";
        try (Connection admin = DriverManager.getConnection(adminUrl, "postgres", "123456"); Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS conversation_db WITH (FORCE)");
            statement.execute("CREATE DATABASE conversation_db");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/conversation_db", "postgres", "123456"); Statement statement = connection.createStatement()) {
            for (String migration : new String[] { "V1__baseline.sql", "V2__conversation_core.sql" }) {
                String sql = Files.readString(Path.of("services/conversation-service/src/main/resources/db/migration", migration));
                for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
            }
            statement.execute("DO $$ BEGIN CREATE ROLE conversation_user LOGIN PASSWORD 'conversation_test_password'; EXCEPTION WHEN duplicate_object THEN NULL; END $$");
            statement.execute("REVOKE ALL ON SCHEMA public FROM conversation_user");
            statement.execute("GRANT USAGE ON SCHEMA public TO conversation_user");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON conversation, message, conversation_outbox, legacy_conversation_migration, legacy_message_migration TO conversation_user");
            try (ResultSet tables = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('conversation', 'message', 'conversation_outbox', 'legacy_conversation_migration', 'legacy_message_migration')")) {
                tables.next(); if (tables.getInt(1) != 5) throw new IllegalStateException("V1 and V2 tables are incomplete");
            }
        }
        try (Connection user = DriverManager.getConnection("jdbc:postgresql://localhost:5432/conversation_db", "conversation_user", "conversation_test_password"); Statement statement = user.createStatement()) {
            statement.executeQuery("SELECT COUNT(*) FROM conversation");
        }
        try (Connection user = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "conversation_user", "conversation_test_password"); Statement statement = user.createStatement()) {
            statement.executeQuery("SELECT COUNT(*) FROM conversation");
            throw new IllegalStateException("conversation_user must not read the legacy database");
        } catch (java.sql.SQLException expected) {
            // Expected: the isolated role has no legacy schema/table privilege.
        }
        System.out.println("conversation_db migration and conversation_user isolation passed");
    }
}
