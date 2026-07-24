package com.macro.mall.portal.aftersale.core;

import com.macro.mall.portal.aftersale.persistence.InMemoryAfterSaleStore;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public final class AfterSaleService {
    private static final String TOPIC = "mall.aftersale.v1";
    private static final BigDecimal SUPERVISOR_THRESHOLD = new BigDecimal("500.00");
    private static final Map<AfterSaleStatus, EnumSet<AfterSaleStatus>> TRANSITIONS = transitions();
    private final InMemoryAfterSaleStore store;
    private final RuleVersionResolver ruleVersions;
    private final OrderCancellationPort cancellations;
    private int cancelledOrders;

    public AfterSaleService(InMemoryAfterSaleStore store, RuleVersionResolver ruleVersions, OrderCancellationPort cancellations) {
        this.store = store;
        this.ruleVersions = ruleVersions;
        this.cancellations = cancellations;
    }

    public AfterSaleCase createDraft(AfterSaleCommand command) {
        String existing = store.commandResult(command.commandId());
        if (existing == null) existing = store.idempotencyResult(command.tenantId() + ':' + command.idempotencyKey());
        if (existing != null) return find(existing);
        validateCommand(command);
        if (store.hasActiveOrder(command.orderId())) return reject("active case conflict");
        String caseId = "test-case-" + UUID.randomUUID();
        AfterSaleCase created = new AfterSaleCase(caseId, command.tenantId(), command.actorId(), command.orderId(), command.amount(),
                command.paid(), command.highRisk(), ruleVersions.resolve(command), AfterSaleStatus.WAITING_USER_CONFIRMATION);
        store.transaction(() -> {
            store.save(created);
            store.remember(command.commandId(), command.tenantId() + ':' + command.idempotencyKey(), caseId);
            store.event(TOPIC + ":UserConfirmationRequired:" + caseId);
        });
        return created;
    }

    public AfterSaleCase confirm(String caseId) {
        AfterSaleCase current = find(caseId);
        requireTransition(current, AfterSaleStatus.SUBMITTED);
        AfterSaleStatus target = !current.paid() ? AfterSaleStatus.EXECUTING : approvalRoute(current);
        AfterSaleCase updated = withStatus(current, target);
        store.transaction(() -> { store.save(updated); store.event(TOPIC + ":AfterSaleSubmitted:" + caseId); });
        if (!updated.paid()) execute(caseId);
        return find(caseId);
    }

    public AfterSaleCase approve(String caseId, boolean supervisor) {
        AfterSaleCase current = find(caseId);
        if ((supervisor && current.status() != AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL)
                || (!supervisor && current.status() != AfterSaleStatus.WAITING_STAFF_APPROVAL)) return reject("approval route mismatch");
        AfterSaleCase updated = withStatus(current, AfterSaleStatus.EXECUTING);
        store.transaction(() -> { store.save(updated); store.event(TOPIC + ":AfterSaleApproved:" + caseId); });
        return updated;
    }

    public AfterSaleCase execute(String caseId) {
        AfterSaleCase current = find(caseId);
        if (current.status() != AfterSaleStatus.EXECUTING || current.paid()) return reject("order cancellation is not eligible");
        cancellations.cancel(current);
        cancelledOrders++;
        store.transaction(() -> store.event(TOPIC + ":OrderCancelled:" + caseId));
        return current;
    }

    public AfterSaleCase transition(String caseId, AfterSaleStatus target) {
        AfterSaleCase current = find(caseId);
        requireTransition(current, target);
        AfterSaleCase updated = withStatus(current, target);
        store.transaction(() -> store.save(updated));
        return updated;
    }

    public AfterSaleCase retry(String caseId) { return transition(caseId, AfterSaleStatus.EXECUTING); }

    public void handleRefundSucceeded(String eventId, String caseId) {
        if (!store.claimInbox(eventId, "aftersale-saga")) return;
        store.transaction(() -> { store.advanceSaga(); store.event(TOPIC + ":RefundSucceeded:" + caseId); });
    }

    public void handleBenefitRollbackFailed(String eventId, String caseId) {
        if (!store.claimInbox(eventId, "aftersale-saga")) return;
        AfterSaleCase updated = withStatus(find(caseId), AfterSaleStatus.MANUAL_RECONCILIATION);
        store.transaction(() -> { store.save(updated); store.event(TOPIC + ":ManualReconciliationRequired:" + caseId); });
    }

    public AfterSaleCase handleRetryableFailure(String eventId, String caseId) {
        if (!store.claimInbox(eventId, "aftersale-saga")) return find(caseId);
        AfterSaleCase updated = transition(caseId, AfterSaleStatus.RETRY_PENDING);
        store.transaction(() -> store.event(TOPIC + ":RefundFailed:" + caseId));
        return updated;
    }

    public AfterSaleCase find(String caseId) { return store.findCase(caseId); }
    public int caseCount() { return store.caseCount(); }
    public int outboxSize() { return store.outboxSize(); }
    public int cancelledOrderCount() { return cancelledOrders; }
    public int auditFailureCount() { return store.auditFailureCount(); }
    public int sagaAdvanceCount() { return store.sagaAdvances(); }
    public void failNextTransaction() { store.failNextTransaction(); }

    private void validateCommand(AfterSaleCommand command) {
        if (!"test-tenant".equals(command.tenantId()) || !Long.valueOf(10L).equals(command.actorId())) throw new SecurityException("trusted tenant or member mismatch");
        if (command.amount() == null || command.amount().signum() < 0) throw new IllegalArgumentException("invalid amount");
    }

    private AfterSaleStatus approvalRoute(AfterSaleCase value) {
        return value.highRisk() || value.amount().compareTo(SUPERVISOR_THRESHOLD) >= 0
                ? AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL : AfterSaleStatus.WAITING_STAFF_APPROVAL;
    }

    private AfterSaleCase withStatus(AfterSaleCase value, AfterSaleStatus status) {
        return new AfterSaleCase(value.caseId(), value.tenantId(), value.memberId(), value.orderId(), value.amount(), value.paid(), value.highRisk(), value.ruleVersionId(), status);
    }

    private void requireTransition(AfterSaleCase current, AfterSaleStatus target) {
        if (!TRANSITIONS.getOrDefault(current.status(), EnumSet.noneOf(AfterSaleStatus.class)).contains(target)) reject("illegal transition");
    }

    private <T> T reject(String reason) { store.audit("REJECTED:" + reason); throw new IllegalStateException(reason); }

    private static Map<AfterSaleStatus, EnumSet<AfterSaleStatus>> transitions() {
        Map<AfterSaleStatus, EnumSet<AfterSaleStatus>> values = new EnumMap<>(AfterSaleStatus.class);
        values.put(AfterSaleStatus.WAITING_USER_CONFIRMATION, EnumSet.of(AfterSaleStatus.SUBMITTED, AfterSaleStatus.CANCELLED));
        values.put(AfterSaleStatus.SUBMITTED, EnumSet.of(AfterSaleStatus.WAITING_STAFF_APPROVAL, AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL, AfterSaleStatus.EXECUTING));
        values.put(AfterSaleStatus.WAITING_STAFF_APPROVAL, EnumSet.of(AfterSaleStatus.EXECUTING, AfterSaleStatus.REJECTED));
        values.put(AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL, EnumSet.of(AfterSaleStatus.EXECUTING, AfterSaleStatus.REJECTED));
        values.put(AfterSaleStatus.EXECUTING, EnumSet.of(AfterSaleStatus.COMPLETED, AfterSaleStatus.FAILED, AfterSaleStatus.RETRY_PENDING, AfterSaleStatus.MANUAL_RECONCILIATION));
        values.put(AfterSaleStatus.RETRY_PENDING, EnumSet.of(AfterSaleStatus.EXECUTING, AfterSaleStatus.FAILED));
        return values;
    }
}
