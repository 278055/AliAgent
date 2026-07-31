package com.bn.aliagent.insight.aggregate; import java.time.Instant; import java.util.*;
public record AggregateKey(String tenantId,String metric,int definitionVersion,WindowGranularity granularity,Instant windowStart,SortedMap<String,String> dimensions){public AggregateKey{dimensions=Collections.unmodifiableSortedMap(new TreeMap<>(dimensions));}}
