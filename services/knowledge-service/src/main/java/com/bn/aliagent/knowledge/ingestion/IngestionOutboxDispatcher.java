package com.bn.aliagent.knowledge.ingestion;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;

public final class IngestionOutboxDispatcher {
    private static final int BATCH_SIZE = 100;
    private final IngestionOutboxGateway gateway;
    private final IngestionMessagePublisher publisher;

    public IngestionOutboxDispatcher(IngestionOutboxGateway gateway, IngestionMessagePublisher publisher) {
        this.gateway = gateway;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${knowledge.ingestion.outbox-dispatch-delay:5000}")
    public void dispatchPending() {
        List<IngestionTaskMessage> messages = gateway.pending(BATCH_SIZE);
        for (IngestionTaskMessage message : messages) {
            try {
                publisher.publish(message);
                gateway.markPublished(message.eventId());
            } catch (Exception ignored) {
                // 保留 outbox 记录，下一轮重试投递。
            }
        }
    }
}
