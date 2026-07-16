package com.bn.aliagent.rag.retriever;

/** P3-B 已发布检索响应中的单个可信切片。 */
public record RemoteKnowledgeItem(String documentId, String versionId, String chunkId, String content, float score) {
}
