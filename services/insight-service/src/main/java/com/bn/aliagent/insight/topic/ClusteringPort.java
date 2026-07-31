package com.bn.aliagent.insight.topic;

import java.util.List;

public interface ClusteringPort {
    List<ClusterMembership> cluster(String tenantId, String problemType, List<EmbeddingVector> vectors,
                                    ClusteringParameters parameters);
}
