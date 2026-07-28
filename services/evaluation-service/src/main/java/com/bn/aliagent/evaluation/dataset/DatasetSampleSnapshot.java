package com.bn.aliagent.evaluation.dataset;
import java.util.*;
public record DatasetSampleSnapshot(UUID candidateId,String input,Map<String,Object> expected,Set<String> labels,Set<String> allowedTools,Set<String> prohibitedTools,Map<String,Object> parameterConstraints,Set<String> citationRequirements,Set<String> factAssertions,Set<String> safetyLabels,boolean expectedHumanHandoff,Map<String,Double> weights,Set<String> applicableMetrics) { }
