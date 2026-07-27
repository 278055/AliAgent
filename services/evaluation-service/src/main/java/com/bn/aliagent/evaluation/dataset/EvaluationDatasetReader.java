package com.bn.aliagent.evaluation.dataset;
import java.util.UUID;
public interface EvaluationDatasetReader { PublishedDatasetVersion requirePublished(String tenantId, UUID datasetVersionId); }
