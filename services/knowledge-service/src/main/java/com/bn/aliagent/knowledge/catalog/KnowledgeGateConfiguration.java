package com.bn.aliagent.knowledge.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KnowledgeGateConfiguration {
    @Bean KnowledgeGateDecisionPort knowledgeGateDecisionPort() {
        return (tenantId, versionId, manifestDigest, policyVersion, proof) -> {
            throw new SecurityException("缺少可验证的 PASS Gate Decision");
        };
    }
}
