package com.bn.aliagent.rag.retriever;

/** 远程知识读取不可用时的脱敏归类。 */
public enum RemoteFailure {
    TIMEOUT,
    UNAUTHORIZED,
    HTTP_ERROR,
    PROTOCOL_ERROR,
    NO_GROUNDING,
    MISSING_TRUSTED_CONTEXT
}
