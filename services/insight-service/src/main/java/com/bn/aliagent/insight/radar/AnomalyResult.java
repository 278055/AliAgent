package com.bn.aliagent.insight.radar; import java.util.*; public record AnomalyResult(boolean anomalous,boolean insufficientSample,List<String> reasons,TrendBaseline baseline){ }
