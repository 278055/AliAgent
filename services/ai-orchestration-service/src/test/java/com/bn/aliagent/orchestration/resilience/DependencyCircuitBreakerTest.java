package com.bn.aliagent.orchestration.resilience;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DependencyCircuitBreakerTest {
    @Test
    void sentinelEnabledOpensCircuitAfterFailures() {
        DependencyCircuitBreaker breaker = new DependencyCircuitBreaker(true, 2);
        assertThrows(IllegalStateException.class, () -> breaker.execute(DependencyType.MODEL, () -> { throw new IllegalStateException("down"); }));
        assertThrows(IllegalStateException.class, () -> breaker.execute(DependencyType.MODEL, () -> { throw new IllegalStateException("down"); }));
        assertThrows(DependencyUnavailableException.class, () -> breaker.execute(DependencyType.MODEL, () -> "unused"));
    }
}
