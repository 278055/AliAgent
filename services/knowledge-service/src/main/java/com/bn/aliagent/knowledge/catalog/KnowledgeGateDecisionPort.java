package com.bn.aliagent.knowledge.catalog;

import java.util.UUID;

public interface KnowledgeGateDecisionPort {
    void requirePass(String tenantId, UUID versionId, String manifestDigest, String policyVersion, String proof);
}
