package com.bn.aliagent.insight.intake;

import java.util.Map;
import java.util.Set;

public final class InsightEventPolicy {
    private final Map<String, Set<Integer>> supportedVersions;

    public InsightEventPolicy(Map<String, Set<Integer>> supportedVersions) {
        this.supportedVersions = supportedVersions.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    public void validate(InsightEventEnvelope event) {
        if (!supportedVersions.getOrDefault(event.eventType(), Set.of()).contains(event.eventVersion())) {
            throw new IllegalArgumentException("不支持的事件类型或版本");
        }
    }
}
