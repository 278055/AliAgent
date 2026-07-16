package com.bn.aliagent.knowledge.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngestionProcessorTest {
    @Test
    void 重复事件只处理一次() {
        FakeGateway gateway = new FakeGateway();
        IngestionProcessor processor = new IngestionProcessor(gateway, task -> "一段可解析文本", text -> List.of(text), text -> new float[1024]);
        IngestionTaskMessage message = new IngestionTaskMessage("event-1", 1, "tenant-a", "trace-1", "task-1");

        processor.process(message);
        processor.process(message);

        assertEquals(1, gateway.readyCount);
        assertEquals(1, gateway.chunks.size());
    }

    @Test
    void 解析异常必须将任务和版本标记为失败() {
        FakeGateway gateway = new FakeGateway();
        IngestionProcessor processor = new IngestionProcessor(gateway, task -> { throw new IllegalStateException("损坏文件"); }, text -> List.of(text), text -> new float[1024]);

        processor.process(new IngestionTaskMessage("event-2", 1, "tenant-a", "trace-2", "task-2"));

        assertEquals(1, gateway.failedCount);
        assertEquals("损坏文件", gateway.failureReason);
        assertEquals(0, gateway.readyCount);
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
