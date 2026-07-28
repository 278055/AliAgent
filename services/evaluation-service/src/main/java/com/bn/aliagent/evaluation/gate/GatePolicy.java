package com.bn.aliagent.evaluation.gate;

import java.util.Map;
import java.util.Objects;

public record GatePolicy(String version, Map<String, Double> maximumRelativeRegression, boolean requireDashScopeResult) {
    public GatePolicy {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("策略版本不能为空");
        }
        maximumRelativeRegression = Map.copyOf(Objects.requireNonNull(maximumRelativeRegression));
        if (maximumRelativeRegression.values().stream().anyMatch(value -> value == null || value < 0 || value > 1)) {
            throw new IllegalArgumentException("相对退化阈值必须介于 0 和 1 之间");
        }
    }
}
