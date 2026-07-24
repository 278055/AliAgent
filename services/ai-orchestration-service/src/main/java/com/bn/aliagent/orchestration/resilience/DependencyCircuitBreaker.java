package com.bn.aliagent.orchestration.resilience;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DependencyCircuitBreaker {
    private final boolean sentinelEnabled;
    private final int failureThreshold;
    private final Map<DependencyType, Integer> failures = new EnumMap<>(DependencyType.class);
    @Autowired
    public DependencyCircuitBreaker(@Value("${orchestration.resilience.sentinel-enabled:false}") boolean sentinelEnabled) { this(sentinelEnabled, 3); }
    DependencyCircuitBreaker(boolean sentinelEnabled, int failureThreshold) { this.sentinelEnabled = sentinelEnabled; this.failureThreshold = failureThreshold; }
    public synchronized <T> T execute(DependencyType dependency, Supplier<T> call) {
        if (sentinelEnabled && failures.getOrDefault(dependency, 0) >= failureThreshold) throw new DependencyUnavailableException(dependency);
        try { T result = call.get(); failures.remove(dependency); return result; }
        catch (RuntimeException exception) { failures.merge(dependency, 1, Integer::sum); throw exception; }
    }
}
