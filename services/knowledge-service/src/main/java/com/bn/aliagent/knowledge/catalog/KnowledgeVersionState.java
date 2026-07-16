package com.bn.aliagent.knowledge.catalog;

public enum KnowledgeVersionState {
    DRAFT, PROCESSING, READY_FOR_REVIEW, PUBLISHED, RETIRED, FAILED;

    public boolean isSearchVisible() {
        return this == PUBLISHED;
    }
}
