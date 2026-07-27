package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.EvaluationManifest;
import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.util.List;
import java.util.UUID;

/** 编排已发布快照的离线回放，不接收草稿或外部凭证。 */
public final class EvaluationRunService {
    private final DatasetSnapshotPort datasets;
    private final OrchestrationReplayPort runner;
    private final EvaluationRunRepository repository;

    public EvaluationRunService(DatasetSnapshotPort datasets, OrchestrationReplayPort runner) {
        this(datasets, runner, null);
    }

    public EvaluationRunService(DatasetSnapshotPort datasets, OrchestrationReplayPort runner, EvaluationRunRepository repository) {
        this.datasets = datasets;
        this.runner = runner;
        this.repository = repository;
    }

    public List<ReplayResult> run(String tenantId, EvaluationManifest manifest) {
        return datasets.published(tenantId, manifest.datasetVersionId()).stream().map(fixture -> runner.replay(tenantId, manifest, fixture)).toList();
    }

    public UUID startMock(String tenantId, EvaluationManifest manifest) {
        if (repository == null) throw new IllegalStateException("evaluation run repository is not configured");
        UUID runId = repository.create(tenantId, manifest, "MOCK");
        for (ReplayFixture fixture : datasets.published(tenantId, manifest.datasetVersionId())) repository.storeResult(tenantId, runId, fixture, runner.replay(tenantId, manifest, fixture));
        repository.complete(tenantId, runId);
        return runId;
    }
}
