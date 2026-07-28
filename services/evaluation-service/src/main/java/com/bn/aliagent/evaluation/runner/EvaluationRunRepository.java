package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.util.UUID;

public interface EvaluationRunRepository {
    UUID create(String tenantId, EvaluationManifest manifest, String mode);
    void storeResult(String tenantId, UUID runId, ReplayFixture fixture, ReplayResult result);
    void complete(String tenantId, UUID runId);
}
