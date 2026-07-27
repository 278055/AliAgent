package com.bn.aliagent.evaluation.dataset;
import java.util.*;
public record EvaluationDataset(UUID id,String tenantId,String name,boolean published,List<UUID> candidateIds) { }
