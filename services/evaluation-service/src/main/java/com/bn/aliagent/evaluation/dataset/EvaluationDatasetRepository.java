package com.bn.aliagent.evaluation.dataset;

import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import java.util.UUID;

/** 数据库 profile 使用的评测集工作流持久化端口。 */
public interface EvaluationDatasetRepository {
    EvaluationDataset createDraft(String tenantId, String name);
    void addCandidate(String tenantId, UUID draftId, UUID candidateId);
    PublishedDatasetVersion publish(String tenantId, UUID draftId, boolean publiclyShared, DatasetShareAuthorization authorization);
    PublishedDatasetVersion requirePublished(String tenantId, UUID datasetVersionId);
}
