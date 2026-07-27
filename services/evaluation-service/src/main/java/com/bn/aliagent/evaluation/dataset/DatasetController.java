package com.bn.aliagent.evaluation.dataset;

import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import java.util.UUID;

/** 由集成会话把此门面装配到受保护 HTTP 路由。 */
public final class DatasetController {
    private final EvaluationDatasetService datasets;
    public DatasetController(EvaluationDatasetService datasets) { this.datasets = datasets; }
    public EvaluationDataset createDraft(String name, TrustedDatasetContext context) { return datasets.createDraft(context.tenantId(), name); }
    public void addCandidate(UUID datasetId, UUID candidateId, TrustedDatasetContext context) { datasets.addCandidate(context.tenantId(), datasetId, candidateId); }
    public PublishedDatasetVersion publish(UUID datasetId, boolean publiclyShared, DatasetShareAuthorization authorization, TrustedDatasetContext context) {
        return datasets.publish(context.tenantId(), datasetId, publiclyShared, authorization);
    }
    public record TrustedDatasetContext(String tenantId, String subjectId) {
        public TrustedDatasetContext { if (tenantId == null || tenantId.isBlank() || subjectId == null || subjectId.isBlank()) throw new SecurityException("缺少可信身份上下文"); }
    }
}
