package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class JdbcKeywordRetriever implements KeywordRetriever {
    static final String FILTER = " FROM knowledge_chunk chunk JOIN knowledge_version version ON version.id = chunk.version_id "
            + "WHERE chunk.tenant_id = ? AND version.tenant_id = chunk.tenant_id AND version.state = 'PUBLISHED' "
            + "AND EXISTS (SELECT 1 FROM knowledge_authorization_snapshot snapshot "
            + "JOIN knowledge_authorization_snapshot_domain snapshot_domain ON snapshot_domain.snapshot_id = snapshot.id "
            + "JOIN knowledge_document_domain document_domain ON document_domain.domain_id = snapshot_domain.domain_id "
            + "WHERE snapshot.id = ? AND snapshot.tenant_id = chunk.tenant_id AND snapshot.subject_id = ? AND snapshot.subject_type = ? "
            + "AND snapshot.roles_csv = ? AND snapshot.permissions_csv = ? AND snapshot.issued_at <= CURRENT_TIMESTAMP AND snapshot.expires_at > CURRENT_TIMESTAMP "
            + "AND snapshot_domain.tenant_id = chunk.tenant_id AND snapshot_domain.permission = 'KNOWLEDGE_READ' "
            + "AND document_domain.tenant_id = chunk.tenant_id AND document_domain.document_id = version.document_id) ";
    private final JdbcTemplate jdbc;

    public JdbcKeywordRetriever(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetrievalCandidate> retrieve(String query, TrustedKnowledgeRequestContext context, int limit) {
        String sql = "SELECT version.document_id, version.id, chunk.id, chunk.content, ts_rank_cd(chunk.content_tsv, websearch_to_tsquery('simple', ?)) "
                + FILTER + "AND chunk.content_tsv @@ websearch_to_tsquery('simple', ?) ORDER BY 5 DESC, chunk.id ASC LIMIT ?";
        return jdbc.query(sql, (rs, row) -> new RetrievalCandidate(rs.getObject(1, java.util.UUID.class), rs.getObject(2, java.util.UUID.class),
                rs.getObject(3, java.util.UUID.class), rs.getString(4), rs.getDouble(5)), parameters(context, query, query, limit));
    }

    static Object[] parameters(TrustedKnowledgeRequestContext context, Object scoreParameter, Object... tail) {
        Object[] parameters = new Object[7 + tail.length];
        parameters[0] = scoreParameter;
        parameters[1] = context.tenantId(); parameters[2] = context.authorizationSnapshotId(); parameters[3] = context.subjectId();
        parameters[4] = context.subjectType(); parameters[5] = context.roles(); parameters[6] = context.permissions();
        System.arraycopy(tail, 0, parameters, 7, tail.length);
        return parameters;
    }
}
