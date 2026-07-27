package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;

public record ModelReplayRequest(String tenantId, EvaluationManifest manifest, ReplayFixture fixture) { }
