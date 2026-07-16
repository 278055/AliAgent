package com.bn.aliagent.knowledge.retrieval;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import com.pgvector.PGvector;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class JdbcSemanticRetriever implements SemanticRetriever {
    private final JdbcTemplate jdbc;

    public JdbcSemanticRetriever(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RetrievalCandidate> retrieve(String query, float[] embedding, TrustedKnowledgeRequestContext context, int limit) {
        String sql = "SELECT version.document_id, version.id, chunk.id, chunk.content, 1 - (chunk.embedding <=> ?) "
                + JdbcKeywordRetriever.FILTER + "ORDER BY chunk.embedding <=> ?, chunk.id ASC LIMIT ?";
        PGvector vector = new PGvector(embedding);
        return jdbc.query(sql, (rs, row) -> new RetrievalCandidate(rs.getObject(1, java.util.UUID.class), rs.getObject(2, java.util.UUID.class),
                rs.getObject(3, java.util.UUID.class), rs.getString(4), rs.getDouble(5)), JdbcKeywordRetriever.parameters(context, vector, vector, limit));
    }
}
