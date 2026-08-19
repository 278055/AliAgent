package com.bn.aliagent.insight.topic;

import java.util.List;

public final class TopicClusterService {
    private final TopicRuleClassifier classifier;
    private final EmbeddingPort embeddings;
    private final ClusteringPort clustering;

    public TopicClusterService(TopicRuleClassifier classifier, EmbeddingPort embeddings, ClusteringPort clustering) {
        this.classifier = classifier;
        this.embeddings = embeddings;
        this.clustering = clustering;
    }

    public TopicCluster cluster(TopicInput input, TopicClusteringVersion version) {
        TopicRisk risk = classifier.classify(input.anonymizedText());
        if (risk != TopicRisk.NORMAL) return snapshot("rule-" + input.memberId(), input, risk, version, List.of(input.memberId()));
        try {
            EmbeddingVector vector = embeddings.embed(input.tenantId(), input.anonymizedText(), version.embeddingVersion());
            vector = new EmbeddingVector(input.tenantId(), input.memberId(), vector.modelVersion(), vector.values());
            List<ClusterMembership> memberships = clustering.cluster(input.tenantId(), "GENERAL", List.of(vector), version.parameters());
            String clusterId = memberships.isEmpty() ? "pending-" + input.memberId() : memberships.get(0).clusterId();
            List<String> members = memberships.isEmpty() ? List.of(input.memberId()) : memberships.stream().map(ClusterMembership::memberId).toList();
            return snapshot(clusterId, input, risk, version, members);
        } catch (RuntimeException ignored) {
            // 向量依赖不可用时只保留原始成员，绝不推断其他成员或改变风险分类。
            return snapshot("pending-" + input.memberId(), input, risk, version, List.of(input.memberId()));
        }
    }

    private TopicCluster snapshot(String clusterId, TopicInput input, TopicRisk risk, TopicClusteringVersion version, List<String> members) {
        return new TopicCluster(clusterId, input.tenantId(), risk, version.ruleVersion(), version.embeddingVersion(),
                version.algorithmVersion(), List.copyOf(members));
    }
}
