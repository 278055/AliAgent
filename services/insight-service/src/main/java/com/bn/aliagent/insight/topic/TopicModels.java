package com.bn.aliagent.insight.topic;

import java.util.List;

record TopicInput(String tenantId, String memberId, String anonymizedText) { }
record EmbeddingVector(String tenantId, String memberId, String modelVersion, List<Double> values) { }
record ClusterMembership(String clusterId, String memberId) { }
record ClusteringParameters(double similarityThreshold, int minimumMembers) { }
record TopicClusteringVersion(String ruleVersion, String embeddingVersion, String algorithmVersion, ClusteringParameters parameters) { }
record TopicCluster(String clusterId, String tenantId, TopicRisk risk, String ruleVersion, String embeddingVersion,
                    String algorithmVersion, List<String> members) { }
enum TopicRisk { NORMAL, PRIVACY, FRAUD, REGULATORY, FUNDS }
