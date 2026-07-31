package com.bn.aliagent.insight.topic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicClusterServiceTest {
    @Test
    void rulesClassifyHighRiskTextWithoutEmbedding() {
        var embeddings = new RecordingEmbeddingPort();
        var service = new TopicClusterService(new TopicRuleClassifier(), embeddings,
                (tenant, type, vectors, parameters) -> List.of());

        var cluster = service.cluster(new TopicInput("test-tenant", "m1", "我的手机号被泄露，担心隐私问题"),
                new TopicClusteringVersion("rules-v1", "embedding-v1", "cluster-v1", new ClusteringParameters(0.8, 2)));

        assertEquals(TopicRisk.PRIVACY, cluster.risk());
        assertEquals(0, embeddings.calls);
        assertTrue(cluster.members().contains("m1"));
    }

    @Test
    void tenantScopedClustersKeepVersionedMemberSnapshot() {
        var service = new TopicClusterService(new TopicRuleClassifier(),
                (tenant, text, model) -> new EmbeddingVector(tenant, text, model, List.of(0.1, 0.2)),
                (tenant, type, vectors, parameters) -> List.of(new ClusterMembership("cluster-a", vectors.get(0).memberId())));

        var cluster = service.cluster(new TopicInput("test-tenant", "m2", "物流一直没有更新"),
                new TopicClusteringVersion("rules-v1", "embedding-v1", "cluster-v1", new ClusteringParameters(0.8, 2)));

        assertEquals("test-tenant", cluster.tenantId());
        assertEquals("embedding-v1", cluster.embeddingVersion());
        assertEquals("cluster-v1", cluster.algorithmVersion());
        assertEquals(List.of("m2"), cluster.members());
    }

    private static final class RecordingEmbeddingPort implements EmbeddingPort {
        private int calls;
        public EmbeddingVector embed(String tenantId, String anonymizedText, String modelVersion) {
            calls++;
            return new EmbeddingVector(tenantId, anonymizedText, modelVersion, List.of());
        }
    }
}
