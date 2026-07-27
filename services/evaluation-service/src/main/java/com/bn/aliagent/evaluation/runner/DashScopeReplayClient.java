package com.bn.aliagent.evaluation.runner;

/** 生产适配器可在此边界使用 API Key；请求、结果和领域对象均不携带凭证。 */
public interface DashScopeReplayClient {
    ModelReplayResponse execute(ModelReplayRequest request);
}
