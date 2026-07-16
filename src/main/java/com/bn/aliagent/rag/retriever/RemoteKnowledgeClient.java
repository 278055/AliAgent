package com.bn.aliagent.rag.retriever;

/** 旧单体访问 P3-B 检索契约的边界。 */
public interface RemoteKnowledgeClient {
    RemoteKnowledgeResult retrieve(String query, int topK, TrustedKnowledgeContext trustedContext);
}
