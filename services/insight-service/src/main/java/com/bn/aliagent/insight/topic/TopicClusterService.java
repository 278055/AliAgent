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
        EmbeddingVector vector = embeddings.embed(input.tenantId(), input.anonymizedText(), version.embeddingVersion());
        vector = new EmbeddingVector(input.tenantId(), input.memberId(), vector.modelVersion(), vector.values());
        List<ClusterMembership> memberships = clustering.cluster(input.tenantId(), "GENERAL", List.of(vector), version.parameters());
        String clusterId = memberships.isEmpty() ? "cluster-" + input.memberId() : memberships.get(0).clusterId();
        return snapshot(clusterId, input, risk, version, memberships.stream().map(ClusterMembership::memberId).toList());
    }

    private TopicCluster snapshot(String clusterId, TopicInput input, TopicRisk risk, TopicClusteringVersion version, List<String> members) {
        return new TopicCluster(clusterId, input.tenantId(), risk, version.ruleVersion(), version.embeddingVersion(),
                version.algorithmVersion(), List.copyOf(members));
    }
}
