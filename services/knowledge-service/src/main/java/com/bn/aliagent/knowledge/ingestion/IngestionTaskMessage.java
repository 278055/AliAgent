package com.bn.aliagent.knowledge.ingestion;

public record IngestionTaskMessage(String eventId, int eventVersion, String tenantId, String traceId, String taskId) {
    public IngestionTaskMessage {
        if (eventId == null || eventId.isBlank() || eventVersion != 1 || tenantId == null || tenantId.isBlank()
                || traceId == null || traceId.isBlank() || taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("摄入任务消息不符合 v1 信封");
        }
    }
}
