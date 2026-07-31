package com.bn.aliagent.insight.fact;

import java.util.Set;

public record InsightAnonymizationPolicy(String ruleVersion, Set<String> sensitiveKeys, Set<String> correlationKeys) {
    public static InsightAnonymizationPolicy v1() {
        return new InsightAnonymizationPolicy("insight-anon-v1",
                Set.of("phone", "mobile", "email", "address", "token", "authorization", "password", "idcard", "identitycard"),
                Set.of("orderid", "conversationid", "userid", "agentid"));
    }
}
