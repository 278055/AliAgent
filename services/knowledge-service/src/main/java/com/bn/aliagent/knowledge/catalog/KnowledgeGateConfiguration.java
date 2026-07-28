package com.bn.aliagent.knowledge.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
class KnowledgeGateConfiguration {
    @Bean KnowledgeGateDecisionPort knowledgeGateDecisionPort(@Value("${knowledge.evaluation.base-url}") String baseUrl,
            @Value("${SERVICE_JWT_SECRET:}") String serviceJwtSecret) {
        return new EvaluationKnowledgeGateClient(baseUrl, serviceJwtSecret);
    }
}
