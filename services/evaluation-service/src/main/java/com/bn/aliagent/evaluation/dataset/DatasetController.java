package com.bn.aliagent.evaluation.dataset;

import com.bn.aliagent.evaluation.anonymization.DatasetShareAuthorization;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** 发布授权仅从服务认证后的可信上下文派生。 */
public final class DatasetController {
    private final EvaluationDatasetService datasets;
    public DatasetController(EvaluationDatasetService datasets) { this.datasets = datasets; }
    public EvaluationDataset createDraft(String name, TrustedDatasetContext context) { return datasets.createDraft(context.tenantId(), name); }
    public void addCandidate(UUID datasetId, UUID candidateId, TrustedDatasetContext context) { datasets.addCandidate(context.tenantId(), datasetId, candidateId); }
    public PublishedDatasetVersion publish(UUID datasetId, boolean publiclyShared, TrustedDatasetContext context) {
        return datasets.publish(context.tenantId(), datasetId, publiclyShared, publiclyShared ? context.publicationAuthorization() : null);
    }
    public record TrustedDatasetContext(String tenantId, String subjectId, Set<String> permissions) {
        public TrustedDatasetContext(String tenantId, String subjectId) { this(tenantId, subjectId, Set.of()); }
        public TrustedDatasetContext { if (tenantId == null || tenantId.isBlank() || subjectId == null || subjectId.isBlank()) throw new SecurityException("missing trusted identity context"); permissions = Set.copyOf(permissions); }
        DatasetShareAuthorization publicationAuthorization() { if (!permissions.contains("EVALUATION_DATASET_PUBLIC_PUBLISH")) throw new SecurityException("public publication is not authorized"); return DatasetShareAuthorization.active(subjectId, Instant.now()); }
    }
}
