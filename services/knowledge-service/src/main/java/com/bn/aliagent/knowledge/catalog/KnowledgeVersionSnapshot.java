package com.bn.aliagent.knowledge.catalog;

import java.util.UUID;

/** 发布时固化的知识版本摘要，评测只允许按该 ID 读取。 */
public record KnowledgeVersionSnapshot(UUID knowledgeVersionId, String tenantId, String documentVersionsDigest,
        String indexingConfigurationDigest, String embeddingVersion, String hybridRetrievalDigest,
        String rerankerConfigurationDigest, String contentDigest) { }
