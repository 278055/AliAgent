package com.bn.aliagent.orchestration.resilience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class SafeDegradationPolicy {
    private final boolean sentinelEnabled;
    public SafeDegradationPolicy(@Value("${orchestration.resilience.sentinel-enabled:false}") boolean sentinelEnabled) { this.sentinelEnabled = sentinelEnabled; }
    public boolean sentinelEnabled() { return sentinelEnabled; }
    public DegradationDecision decide(DependencyType dependency) {
        return switch (dependency) {
            case MODEL -> DegradationDecision.HUMAN_HANDOFF;
            case KNOWLEDGE -> DegradationDecision.NO_UNSUPPORTED_POLICY;
            case MALL -> DegradationDecision.NO_UNSUPPORTED_MALL_FACTS;
        };
    }
}
