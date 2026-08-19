package com.bn.aliagent.insight.topic;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** 使用 pgvector 的余弦距离检索已有匿名成员，数据库故障时返回空结果供上层降级。 */
public final class JdbcPgVectorClusteringPort implements ClusteringPort {
    private final JdbcTemplate jdbc;

    public JdbcPgVectorClusteringPort(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<ClusterMembership> cluster(String tenantId, String problemType, List<EmbeddingVector> vectors,
                                           ClusteringParameters parameters) {
        if (vectors.isEmpty()) return List.of();
        try {
            List<ClusterMembership> memberships = new ArrayList<>();
            for (EmbeddingVector vector : vectors) {
                String encoded = vector.values().toString();
                memberships.addAll(jdbc.query("SELECT cluster_id, member_ref FROM insight_topic_vector "
                                + "WHERE tenant_id=? AND problem_type=? AND 1 - (embedding <=> CAST(? AS public.vector)) >= ? "
                                + "ORDER BY embedding <=> CAST(? AS public.vector) LIMIT 1",
                        (rs, row) -> new ClusterMembership(rs.getString(1), rs.getString(2)),
                        tenantId, problemType, encoded, parameters.similarityThreshold(), encoded));
            }
            return List.copyOf(memberships);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }
}
