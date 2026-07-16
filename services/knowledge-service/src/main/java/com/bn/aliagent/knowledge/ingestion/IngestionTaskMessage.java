package com.bn.aliagent.knowledge.ingestion;

/** 统一 v1 事件信封中的知识摄入请求。 */
public record IngestionTaskMessage(String eventId, String eventType, int eventVersion, String occurredAt, String tenantId,
        String traceId, String producer, IngestionPayload payload) {
    public static final String EVENT_TYPE = "KnowledgeIngestionRequested";
    public static final String PRODUCER = "knowledge-service";

    public IngestionTaskMessage {
        if (eventId == null || eventId.isBlank() || !EVENT_TYPE.equals(eventType) || eventVersion != 1
                || occurredAt == null || occurredAt.isBlank() || tenantId == null || tenantId.isBlank()
                || traceId == null || traceId.isBlank() || !PRODUCER.equals(producer) || payload == null) {
            throw new IllegalArgumentException("摄入任务消息不符合 v1 信封");
        }
    }

    public String taskId() {
        return payload.taskId();
    }

    public record IngestionPayload(String taskId) {
        public IngestionPayload {
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("摄入任务缺少 taskId");
            }
        }
    }
}
