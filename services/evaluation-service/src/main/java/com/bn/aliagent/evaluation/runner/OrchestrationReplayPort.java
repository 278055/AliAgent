package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;

/** 集成时由 P5 隔离回放入口适配，禁止触碰线上会话。 */
public interface OrchestrationReplayPort {
    ReplayResult replay(String tenantId, EvaluationManifest manifest, ReplayFixture fixture);
}
