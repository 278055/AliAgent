package com.bn.aliagent.conversation.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public final class HumanCollaborationOutboxDispatcher {
    private final HumanCollaborationOutbox outbox;
    private final RabbitTemplate rabbit;
    public HumanCollaborationOutboxDispatcher(HumanCollaborationOutbox outbox, RabbitTemplate rabbit) { this.outbox = outbox; this.rabbit = rabbit; }
    @Scheduled(fixedDelayString = "${conversation.human-outbox.dispatch-delay:3000}")
    public void dispatch() {
        for (HumanCollaborationEvent event : outbox.pending(100)) {
            try {
                if ("copilot.suggestion.requested.v2".equals(event.eventType())) {
                    rabbit.convertAndSend("copilot.suggestion.requested.v2", copilotEvent(event));
                } else if (isP9Event(event.eventType())) {
                    rabbit.convertAndSend("insight.events.v1", P9InsightEventMapper.map(event));
                } else rabbit.convertAndSend("conversation.human.events.v1", event);
                outbox.markPublished(event.eventId());
            }
            catch (RuntimeException ignored) { /* 保留待发记录，等待下次调度补发。 */ }
        }
    }
    private java.util.Map<String, Object> copilotEvent(HumanCollaborationEvent event) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("conversationId", event.conversationId().toString()); payload.put("triggerMessageId", event.requestId().toString());
        payload.put("assignedAgentId", event.sender()); payload.put("requestId", event.requestId().toString());
        payload.put("refreshNo", 0); payload.put("modelVersion", "p7-model-v1"); payload.put("promptVersion", "p7-prompt-v1");
        payload.put("workflowVersion", "p7-workflow-v1"); payload.put("authorizationSnapshotId", event.authorizationSnapshotId().toString());
        payload.put("subjectId", event.subjectId()); payload.put("subjectType", event.subjectType());
        payload.put("roles", event.roles()); payload.put("permissions", event.permissions());
        return java.util.Map.of("eventId", event.eventId().toString(), "eventVersion", 2, "tenantId", event.tenantId(), "payload", payload);
    }
    private static boolean isP9Event(String type) { return "conversation.completed".equals(type) || "conversation.feedback.received".equals(type) || "conversation.human.requested".equals(type) || "conversation.human.first-public-reply".equals(type); }
}
