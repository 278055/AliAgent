package com.macro.mall.portal.aftersale.notification;

import com.macro.mall.portal.aftersale.api.NotificationCommand;
import com.macro.mall.portal.aftersale.api.NotificationPort;
import com.macro.mall.portal.aftersale.api.StepResult;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 仅模拟通知提交，绝不把提交成功表述为用户已收到。 */
@Component
public final class MockNotificationAdapter implements NotificationPort {
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "AFTERSALE_SUBMITTED", "APPROVAL_RESULT", "REFUND_RESULT",
            "BENEFIT_FAILURE", "MANUAL_RECONCILIATION");
    private final Map<String, StepResult> submissions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final AtomicInteger submissionCount = new AtomicInteger();

    @Override
    public StepResult notify(NotificationCommand command) {
        validate(command);
        String businessKey = command.tenantId() + ':' + command.idempotencyKey();
        return submissions.compute(businessKey, (key, existing) -> {
            if (existing != null && "SUBMITTED".equals(existing.status())) return existing;
            return submit(command);
        });
    }

    public int submissionCount() {
        return submissionCount.get();
    }

    private StepResult submit(NotificationCommand command) {
        submissionCount.incrementAndGet();
        int attempt = attempts.computeIfAbsent(command.idempotencyKey(), ignored -> new AtomicInteger()).incrementAndGet();
        if (command.idempotencyKey().contains("fail") && (!command.idempotencyKey().contains("fail-once") || attempt == 1)) {
            return new StepResult("RETRY_PENDING", "MOCK_NOTIFICATION_REJECTED");
        }
        return new StepResult("SUBMITTED", "MOCK_NOTIFICATION_ACCEPTED");
    }

    private void validate(NotificationCommand command) {
        if (command == null || command.caseId() == null || command.tenantId() == null || command.tenantId().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || !SUPPORTED_EVENTS.contains(command.eventType())) {
            throw new IllegalArgumentException("invalid mock notification command");
        }
    }
}
