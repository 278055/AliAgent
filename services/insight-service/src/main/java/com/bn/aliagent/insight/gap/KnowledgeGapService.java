package com.bn.aliagent.insight.gap;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KnowledgeGapService {
    private final Map<String, KnowledgeGap> gaps = new ConcurrentHashMap<>();

    public KnowledgeGap record(String tenantId, String problemType, GapSignal signal, String knowledgeVersion) {
        String key = tenantId + "|" + problemType;
        return gaps.compute(key, (ignored, existing) -> {
            var signals = new LinkedHashSet<GapSignal>();
            if (existing != null) signals.addAll(existing.signals());
            signals.add(signal);
            return new KnowledgeGap(existing == null ? UUID.randomUUID() : existing.gapId(), tenantId, problemType, Set.copyOf(signals),
                    knowledgeVersion, signals.size(), GapStatus.DRAFT, false);
        });
    }
}
