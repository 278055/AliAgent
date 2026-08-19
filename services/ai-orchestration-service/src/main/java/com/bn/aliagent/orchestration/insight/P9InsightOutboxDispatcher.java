package com.bn.aliagent.orchestration.insight;

import java.util.function.Consumer;
import org.springframework.scheduling.annotation.Scheduled;

public final class P9InsightOutboxDispatcher {
    private final P9InsightOutbox outbox; private final Consumer<P9InsightEvent> publisher;
    public P9InsightOutboxDispatcher(P9InsightOutbox outbox, Consumer<P9InsightEvent> publisher) { this.outbox = outbox; this.publisher = publisher; }
    @Scheduled(fixedDelayString = "${orchestration.insight.outbox-dispatch-delay:5000}")
    public void dispatchPending() { for (P9InsightEvent event : outbox.pending(100)) try { publisher.accept(event); outbox.markPublished(event.eventId()); } catch (RuntimeException ignored) { /* 保留待发记录。 */ } }
}
