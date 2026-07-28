package com.bn.aliagent.evaluation.runner;

import java.math.BigDecimal;

public record DashScopeLimits(int maxSamples, long maxInputTokens, long maxOutputTokens, BigDecimal maxCost) {
    public DashScopeLimits {
        if (maxSamples <= 0 || maxInputTokens < 0 || maxOutputTokens < 0 || maxCost == null || maxCost.signum() < 0) {
            throw new IllegalArgumentException("DashScope 限额无效");
        }
    }
}
