package com.bn.aliagent.knowledge.insight;

import java.util.function.Consumer;
import org.springframework.scheduling.annotation.Scheduled;

public final class KnowledgeInsightOutboxDispatcher {
    private final KnowledgeInsightOutbox outbox; private final Consumer<KnowledgeInsightEvent> publisher;
    public KnowledgeInsightOutboxDispatcher(KnowledgeInsightOutbox outbox, Consumer<KnowledgeInsightEvent> publisher) { this.outbox = outbox; this.publisher = publisher; }
    @Scheduled(fixedDelayString = "${knowledge.insight.outbox-dispatch-delay:5000}")
    public void dispatchPending() { for (KnowledgeInsightEvent event : outbox.pending(100)) try { publisher.accept(event); outbox.markPublished(event.eventId()); } catch (RuntimeException ignored) { /* 保留待发记录。 */ } }
}
