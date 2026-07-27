package com.bn.aliagent.evaluation.runner;

import com.bn.aliagent.evaluation.replay.ReplayFixture;
import java.util.List;
import java.util.UUID;

/** 集成时由 A 的已发布数据集适配器实现，B 不引用 A 的领域类型。 */
public interface DatasetSnapshotPort {
    List<ReplayFixture> published(String tenantId, UUID datasetVersionId);
}
