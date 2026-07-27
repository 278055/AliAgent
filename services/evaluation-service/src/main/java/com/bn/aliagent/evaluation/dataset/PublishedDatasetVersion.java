package com.bn.aliagent.evaluation.dataset;
import java.util.*;
public record PublishedDatasetVersion(UUID id,String tenantId,UUID datasetId,int version,String digest,boolean publiclyShared,List<DatasetSampleSnapshot> samples) { public PublishedDatasetVersion { samples=List.copyOf(samples); } }
