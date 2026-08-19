package com.bn.aliagent.insight.topic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcPgVectorClusteringPortTest {
    @Test
    void databaseFailureReturnsNoMembershipRatherThanInventingMembers() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
                throw new IllegalStateException("pgvector unavailable");
            }
        };
        var port = new JdbcPgVectorClusteringPort(jdbc);

        var result = port.cluster("test-tenant", "GENERAL", List.of(new EmbeddingVector("test-tenant", "m1", "v1", List.of(0.1, 0.2))), new ClusteringParameters(0.8, 2));

        assertEquals(List.of(), result);
    }
}
