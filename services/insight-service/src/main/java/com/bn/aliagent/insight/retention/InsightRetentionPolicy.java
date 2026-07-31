package com.bn.aliagent.insight.retention;

public record InsightRetentionPolicy(int retentionDays, int batchSize) {
    public InsightRetentionPolicy {
        if (retentionDays != 90 || batchSize <= 0) throw new IllegalArgumentException("保留期必须为 90 天且批次大于零");
    }
}
