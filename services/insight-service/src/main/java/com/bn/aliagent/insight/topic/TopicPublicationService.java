package com.bn.aliagent.insight.topic;

import java.util.List;

public final class TopicPublicationService {
    private final TopicNamingPort naming;

    public TopicPublicationService(TopicNamingPort naming) { this.naming = naming; }

    public TopicPublicationDecision decide(TopicCandidate candidate, String modelVersion, String promptVersion) {
        if (candidate.risk() != TopicRisk.NORMAL) {
            return new TopicPublicationDecision(TopicState.PENDING_SUPERVISOR_REVIEW, "待主管审核", "高风险主题需主管审核", true,
                    modelVersion, promptVersion, List.copyOf(candidate.memberSnapshot()));
        }
        try {
            TopicNaming result = naming.name(candidate.tenantId(), candidate.memberSnapshot(), modelVersion, promptVersion);
            TopicState state = candidate.evidenceCount() >= 5 && candidate.confidence() >= 0.8 ? TopicState.PUBLISHED : TopicState.PENDING_NAMING;
            return new TopicPublicationDecision(state, result.displayName(), result.summary(), false, modelVersion, promptVersion,
                    List.copyOf(candidate.memberSnapshot()));
        } catch (RuntimeException ignored) {
            return new TopicPublicationDecision(TopicState.PENDING_NAMING, "待命名", "模型暂不可用", false, modelVersion, promptVersion,
                    List.copyOf(candidate.memberSnapshot()));
        }
    }
}

record TopicCandidate(String tenantId, TopicRisk risk, List<String> memberSnapshot, long evidenceCount, double confidence) { }
record TopicNaming(String displayName, String summary) { }
record TopicPublicationDecision(TopicState state, String displayName, String summary, boolean supervisorReviewRequired,
                                String modelVersion, String promptVersion, List<String> memberSnapshot) { }
enum TopicState { PENDING_NAMING, PENDING_SUPERVISOR_REVIEW, PUBLISHED, REJECTED }
