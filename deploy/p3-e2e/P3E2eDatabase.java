import com.pgvector.PGvector;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public final class P3E2eDatabase {
    private static final String TENANT = "rag-test-p3-e2e";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: setup|cleanup|assert-clean <password>");
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:15432/knowledge_db", "knowledge_user", args[1])) {
            if ("setup".equals(args[0])) setup(connection);
            else if ("cleanup".equals(args[0])) cleanup(connection);
            else if ("assert-clean".equals(args[0])) assertClean(connection);
            else throw new IllegalArgumentException("Unsupported command: " + args[0]);
        }
    }

    private static void setup(Connection connection) throws Exception {
        cleanup(connection);
        UUID document = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID domain = UUID.randomUUID();
        UUID snapshot = UUID.randomUUID();
        UUID chunk = UUID.randomUUID();
        try (PreparedStatement documentInsert = connection.prepareStatement("INSERT INTO knowledge_document (id, tenant_id, object_key, original_filename, media_type, content_length, checksum_sha256, uploaded_by) VALUES (?, ?, ?, 'rag-test-p3-e2e.txt', 'text/plain', 10, ?, 'rag-test-subject')");
             PreparedStatement versionInsert = connection.prepareStatement("INSERT INTO knowledge_version (id, tenant_id, document_id, version_number, state, created_by) VALUES (?, ?, ?, 1, 'PUBLISHED', 'rag-test-subject')");
             PreparedStatement domainInsert = connection.prepareStatement("INSERT INTO knowledge_domain (id, tenant_id, code, name) VALUES (?, ?, 'rag-test-domain', 'Rag Test Domain')");
             PreparedStatement documentDomainInsert = connection.prepareStatement("INSERT INTO knowledge_document_domain (tenant_id, document_id, domain_id) VALUES (?, ?, ?)");
             PreparedStatement snapshotInsert = connection.prepareStatement("INSERT INTO knowledge_authorization_snapshot (id, tenant_id, subject_id, subject_type, roles_csv, permissions_csv, issued_at, expires_at) VALUES (?, ?, 'rag-test-subject', 'STAFF', 'KNOWLEDGE_EDITOR', 'knowledge:write', CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP + INTERVAL '10 minutes')");
             PreparedStatement snapshotDomainInsert = connection.prepareStatement("INSERT INTO knowledge_authorization_snapshot_domain (snapshot_id, tenant_id, domain_id, permission) VALUES (?, ?, ?, 'KNOWLEDGE_READ')");
             PreparedStatement chunkInsert = connection.prepareStatement("INSERT INTO knowledge_chunk (id, tenant_id, version_id, sequence_number, content, embedding) VALUES (?, ?, ?, 0, 'Remote policy result from knowledge service.', ?)") ) {
            documentInsert.setObject(1, document); documentInsert.setString(2, TENANT); documentInsert.setString(3, "knowledge/tenant-rag-test-p3-e2e/rag-test-p3-e2e.txt"); documentInsert.setString(4, "0".repeat(64)); documentInsert.executeUpdate();
            versionInsert.setObject(1, version); versionInsert.setString(2, TENANT); versionInsert.setObject(3, document); versionInsert.executeUpdate();
            domainInsert.setObject(1, domain); domainInsert.setString(2, TENANT); domainInsert.executeUpdate();
            documentDomainInsert.setString(1, TENANT); documentDomainInsert.setObject(2, document); documentDomainInsert.setObject(3, domain); documentDomainInsert.executeUpdate();
            snapshotInsert.setObject(1, snapshot); snapshotInsert.setString(2, TENANT); snapshotInsert.executeUpdate();
            snapshotDomainInsert.setObject(1, snapshot); snapshotDomainInsert.setString(2, TENANT); snapshotDomainInsert.setObject(3, domain); snapshotDomainInsert.executeUpdate();
            chunkInsert.setObject(1, chunk); chunkInsert.setString(2, TENANT); chunkInsert.setObject(3, version); chunkInsert.setObject(4, new PGvector(new float[1024])); chunkInsert.executeUpdate();
        }
        System.out.println("snapshot=" + snapshot + " document=" + document + " version=" + version + " chunk=" + chunk);
    }

    private static void assertClean(Connection connection) throws Exception {
        for (String table : new String[] { "knowledge_document", "knowledge_version", "knowledge_chunk", "ingestion_task", "knowledge_domain", "knowledge_authorization_snapshot", "ingestion_outbox", "consumed_event" }) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?")) {
                statement.setString(1, TENANT);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) != 0) throw new IllegalStateException("Test data remains in " + table);
                }
            }
        }
        System.out.println("P3 E2E test data cleanup verified");
    }

    private static void cleanup(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM knowledge_authorization_snapshot_domain WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_authorization_snapshot WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_document_domain WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM ingestion_outbox WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM consumed_event WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM ingestion_task WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_chunk WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_version WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_document WHERE tenant_id = '" + TENANT + "'");
            statement.execute("DELETE FROM knowledge_domain WHERE tenant_id = '" + TENANT + "'");
        }
    }
}
