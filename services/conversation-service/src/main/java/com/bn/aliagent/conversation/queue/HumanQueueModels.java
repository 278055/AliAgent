package com.bn.aliagent.conversation.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class HumanQueueModels {
    private HumanQueueModels() { }
    public enum QueueStatus { WAITING, OFFERED, CLAIMABLE, ASSIGNED, CANCELLED, CLOSED }
    public record PriorityInput(boolean highRisk, boolean complaintRisk, String membershipLevel, Duration waiting) { }
    public record PriorityScore(int score, String ruleVersion) { }
    public static final class PriorityPolicy {
        public static final String RULE_VERSION = "p7-priority-v1";
        public PriorityScore score(PriorityInput input) {
            int membership = "HIGH_VALUE".equals(input.membershipLevel()) ? 200 : "VIP".equals(input.membershipLevel()) ? 100 : 0;
            int waiting = (int) Math.min(300, Math.max(0, input.waiting().toMinutes()));
            return new PriorityScore((input.highRisk() ? 1000 : 0) + (input.complaintRisk() ? 800 : 0) + membership + waiting, RULE_VERSION);
        }
    }
    public record EnqueueCommand(String tenantId, UUID conversationId, UUID skillGroupId, boolean highRisk, boolean complaintRisk,
                                 String membershipLevel, Instant enqueuedAt, UUID requestId) { }
    public record QueueItem(UUID id, String tenantId, UUID conversationId, UUID skillGroupId, int priority, String routingRuleVersion,
                            String priorityRuleVersion, Instant enqueuedAt, UUID requestId, int assignmentAttempts, QueueStatus status) {
        public QueueItem withStatus(QueueStatus value) { return new QueueItem(id, tenantId, conversationId, skillGroupId, priority, routingRuleVersion, priorityRuleVersion, enqueuedAt, requestId, assignmentAttempts, value); }
        public QueueItem nextAttempt(QueueStatus value) { return new QueueItem(id, tenantId, conversationId, skillGroupId, priority, routingRuleVersion, priorityRuleVersion, enqueuedAt, requestId, assignmentAttempts + 1, value); }
    }
}
