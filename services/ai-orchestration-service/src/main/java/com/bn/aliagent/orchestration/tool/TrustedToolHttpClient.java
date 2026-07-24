package com.bn.aliagent.orchestration.tool;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;

/** 复用适配层的可信服务调用，工具仅暴露受控只读请求。 */
public final class TrustedToolHttpClient {
    private final com.bn.aliagent.orchestration.adapter.TrustedHttpClient delegate;
    public TrustedToolHttpClient(String jwt, int timeoutMs, int attempts) { delegate = new com.bn.aliagent.orchestration.adapter.TrustedHttpClient(jwt, timeoutMs, attempts); }
    public String get(String url, OrchestrationContract.ExecutionContext context) { return delegate.get(url, context); }
}
