package com.bn.aliagent.insight.aggregate; import java.time.*;
public interface AggregateRetentionTarget { int deleteHourlyBefore(String tenantId,Instant cutoff,int batchSize); int deleteDailyBefore(String tenantId,LocalDate cutoff,int batchSize); }
