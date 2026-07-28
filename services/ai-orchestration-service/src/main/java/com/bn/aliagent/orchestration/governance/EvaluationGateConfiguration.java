package com.bn.aliagent.orchestration.governance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EvaluationGateConfiguration {
    @Bean
    GateDecisionPort gateDecisionPort(@Value("${orchestration.clients.evaluation-base-url}") String baseUrl,
            @Value("${SERVICE_JWT_SECRET:}") String serviceJwtSecret) {
        return new EvaluationGateDecisionClient(baseUrl, serviceJwtSecret);
    }
}
