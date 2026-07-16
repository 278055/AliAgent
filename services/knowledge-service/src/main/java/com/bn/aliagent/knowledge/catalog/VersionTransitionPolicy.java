package com.bn.aliagent.knowledge.catalog;

public final class VersionTransitionPolicy {
    private VersionTransitionPolicy() {
    }

    public static void requirePublishable(KnowledgeVersionState state) {
        if (state != KnowledgeVersionState.READY_FOR_REVIEW) {
            throw new IllegalStateException("仅审核就绪版本可以发布");
        }
    }
}
