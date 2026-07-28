package com.bn.aliagent.evaluation.anonymization;

import java.util.Set;

public record AnonymizationPolicy(String ruleVersion, Set<String> sensitiveKeys, Set<String> correlationKeys) {
    public static AnonymizationPolicy v1() {
        return new AnonymizationPolicy("anon-v1", Set.of("phone", "email", "address", "idcard", "token", "authorization", "password", "credential"),
                Set.of("userId", "customerId", "agentId", "orderId", "conversationId"));
    }
}
