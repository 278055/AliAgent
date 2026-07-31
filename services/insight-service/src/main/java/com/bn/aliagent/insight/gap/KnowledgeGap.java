package com.bn.aliagent.insight.gap;

import java.util.Set;
import java.util.UUID;

public record KnowledgeGap(UUID gapId, String tenantId, String problemType, Set<GapSignal> signals,
                           String knowledgeVersion, long evidenceCount, GapStatus status, boolean knowledgePublished) { }
record GapSignal(String source, String evidenceRef) { }
enum GapStatus { DRAFT, REVIEWED, RESOLVED }
