package com.bn.aliagent.conversation.core;

import org.springframework.scheduling.annotation.Scheduled;

public final class ConversationOutboxDispatcher {
    private final ConversationRepository repository;
    private final AIReplyRequestedPublisher publisher;

    public ConversationOutboxDispatcher(ConversationRepository repository, AIReplyRequestedPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${conversation.outbox.dispatch-delay:5000}")
    public void dispatchPending() {
        for (var request : repository.pendingReplies(100)) {
            try {
                publisher.publish(request);
                repository.markPublished(request.eventId());
            } catch (Exception ignored) {
                // Retain the outbox record so a later schedule can retry it.
            }
        }
    }
}
