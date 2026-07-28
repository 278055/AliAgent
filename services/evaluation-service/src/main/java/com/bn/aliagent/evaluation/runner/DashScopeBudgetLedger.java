package com.bn.aliagent.evaluation.runner;

import java.math.BigDecimal;

/** 内存账本用于领域限额；集成时替换为持久化、可审计的适配器。 */
public final class DashScopeBudgetLedger {
    private int samples;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal cost = BigDecimal.ZERO;

    public synchronized void reserve(DashScopeLimits limits) {
        if (samples >= limits.maxSamples() || inputTokens >= limits.maxInputTokens() || outputTokens >= limits.maxOutputTokens()
                || cost.compareTo(limits.maxCost()) >= 0) throw new IllegalStateException("DashScope 预算已耗尽");
        samples++;
    }

    public synchronized void record(ModelReplayResponse response) {
        inputTokens += response.inputTokens();
        outputTokens += response.outputTokens();
        cost = cost.add(response.cost());
    }
}
