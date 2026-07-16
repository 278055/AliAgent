package com.bn.aliagent.knowledge.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionProcessorTest {
    @Test
    void 重复事件只处理一次() {
        FakeGateway gateway = new FakeGateway();
        IngestionProcessor processor = new IngestionProcessor(gateway, task -> "一段可解析文本", text -> List.of(text), text -> new float[1024]);
        IngestionTaskMessage message = message("event-1", "task-1");

        processor.process(message);
        processor.process(message);

        assertEquals(1, gateway.readyCount);
        assertEquals(1, gateway.chunks.size());
    }

    @Test
    void 解析异常必须将任务和版本标记为失败() {
        FakeGateway gateway = new FakeGateway();
        IngestionProcessor processor = new IngestionProcessor(gateway, task -> { throw new IOException("storage endpoint leaked"); }, text -> List.of(text), text -> new float[1024]);

        processor.process(message("event-2", "task-2"));

        assertEquals(1, gateway.failedCount);
        assertEquals("INGESTION_SOURCE_UNAVAILABLE", gateway.failureReason);
        assertEquals(0, gateway.readyCount);
    }

    @Test
    void 非存储异常不得将内部信息写入任务诊断() {
        FakeGateway gateway = new FakeGateway();
        IngestionProcessor processor = new IngestionProcessor(gateway,
                task -> { throw new IllegalStateException("internal parser path"); }, text -> List.of(text), text -> new float[1024]);

        processor.process(message("event-4", "task-4"));

        assertEquals("INGESTION_PROCESSING_FAILED", gateway.failureReason);
    }

    @Test
    void 消息必须使用固定事件类型和生产者() {
        assertThrows(IllegalArgumentException.class, () -> new IngestionTaskMessage("event-3", "OtherEvent", 1,
                Instant.now().toString(), "tenant-a", "trace-1", "knowledge-service",
                new IngestionTaskMessage.IngestionPayload("task-3")));
        assertThrows(IllegalArgumentException.class, () -> new IngestionTaskMessage("event-3",
                "KnowledgeIngestionRequested", 1, Instant.now().toString(), "tenant-a", "trace-1", "other-service",
                new IngestionTaskMessage.IngestionPayload("task-3")));
    }

    private IngestionTaskMessage message(String eventId, String taskId) {
        return new IngestionTaskMessage(eventId, "KnowledgeIngestionRequested", 1, Instant.now().toString(), "tenant-a",
                "trace-1", "knowledge-service", new IngestionTaskMessage.IngestionPayload(taskId));
    }

    private static final class FakeGateway implements IngestionTaskGateway {
        private final List<KnowledgeChunk> chunks = new ArrayList<>();
        private boolean consumed;
        private int readyCount;
        private int failedCount;
        private String failureReason;

        @Override public boolean registerConsumption(String eventId, String consumer) { if (consumed) return false; consumed = true; return true; }
        @Override public IngestionSource load(String taskId, String tenantId) { return new IngestionSource(taskId, "version-1", tenantId, "knowledge/tenant-tenant-a/file.txt"); }
        @Override public void replaceChunks(String versionId, String tenantId, List<KnowledgeChunk> values) { chunks.addAll(values); }
        @Override public void markReadyForReview(String taskId, String tenantId) { readyCount++; }
        @Override public void markFailed(String taskId, String tenantId, String diagnostic) { failedCount++; failureReason = diagnostic; }
    }
}
