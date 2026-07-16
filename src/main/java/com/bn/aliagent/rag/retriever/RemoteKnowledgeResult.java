package com.bn.aliagent.rag.retriever;

import java.util.List;

/** 远程检索调用结果；无依据与故障都不向调用方暴露远程内容。 */
public record RemoteKnowledgeResult(List<RemoteKnowledgeItem> items, RemoteFailure failure) {
    public static RemoteKnowledgeResult success(List<RemoteKnowledgeItem> items) {
        return new RemoteKnowledgeResult(List.copyOf(items), null);
    }

    public static RemoteKnowledgeResult noGrounding() {
        return new RemoteKnowledgeResult(List.of(), RemoteFailure.NO_GROUNDING);
    }

    public static RemoteKnowledgeResult failure(RemoteFailure failure) {
        return new RemoteKnowledgeResult(List.of(), failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }
}
