package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.util.List;

/** 编排已发布快照的离线回放，不接收草稿或外部凭证。 */
public final class EvaluationRunService {
    private final DatasetSnapshotPort datasets;
    private final OrchestrationReplayPort runner;

    public EvaluationRunService(DatasetSnapshotPort datasets, OrchestrationReplayPort runner) {
        this.datasets = datasets;
        this.runner = runner;
    }

    public List<ReplayResult> run(String tenantId, EvaluationManifest manifest) {
        return datasets.published(tenantId, manifest.datasetVersionId()).stream().map(fixture -> runner.replay(tenantId, manifest, fixture)).toList();
    }
}
