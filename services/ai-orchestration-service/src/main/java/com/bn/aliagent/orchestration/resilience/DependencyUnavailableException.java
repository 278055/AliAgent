package com.bn.aliagent.orchestration.resilience;

public final class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(DependencyType dependency) { super(dependency + " 熔断中"); }
}
