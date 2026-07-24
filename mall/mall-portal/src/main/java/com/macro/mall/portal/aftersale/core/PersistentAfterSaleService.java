package com.macro.mall.portal.aftersale.core;

import com.macro.mall.portal.aftersale.api.AfterSaleCommandPort;
import com.macro.mall.portal.aftersale.api.AfterSaleQueryPort;
import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.CreateAfterSaleDraft;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import com.macro.mall.portal.aftersale.api.SagaStepReport;
import com.macro.mall.portal.aftersale.api.ManualReconciliationCommand;
import com.macro.mall.portal.aftersale.persistence.AfterSaleJdbcRepository;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** 售后事实、Saga 与 Outbox 在同一 MySQL 本地事务中创建和推进。 */
@Service
public class PersistentAfterSaleService implements AfterSaleCommandPort, AfterSaleQueryPort {
    private static final BigDecimal SUPERVISOR_THRESHOLD = new BigDecimal("500.00");
    private final AfterSaleJdbcRepository repository;
    private final RuleVersionResolver rules;
    private final OrderTenantResolver orderTenants;
    private final OmsOrderMapper orders;
    private final OrderCancellationPort cancellations;

    public PersistentAfterSaleService(AfterSaleJdbcRepository repository, RuleVersionResolver rules, OrderTenantResolver orderTenants, OmsOrderMapper orders, OrderCancellationPort cancellations) {
        this.repository = repository; this.rules = rules; this.orderTenants = orderTenants; this.orders = orders; this.cancellations = cancellations;
    }

    @Override
    @Transactional
    public AfterSaleView createDraft(TrustedAfterSaleContext context, CreateAfterSaleDraft command) {
        requireMember(context); verifyPayload(context, command); verifyOrderOwnership(context, command.orderId());
        Optional<AfterSaleView> byCommand = repository.findByCommand(command.commandId());
        if (byCommand.isPresent()) return byCommand.get();
        Optional<AfterSaleView> byKey = repository.findByIdempotency(context.tenantId(), command.commandType(), command.idempotencyKey());
        if (byKey.isPresent()) return byKey.get();
        if (repository.activeItemExists(context.tenantId(), command.orderItemId())) throw new IllegalStateException("active after-sale case already exists");
        String version = rules.resolve(new AfterSaleCommand(command.commandId(), command.idempotencyKey(), context.tenantId(), context.actorId(), command.orderId(), command.requestedAmount(), false, false));
        String caseNo = "AS-" + UUID.randomUUID().toString().replace("-", "");
        long caseId = repository.insertCase(context, command, caseNo, version, AfterSaleStatus.WAITING_USER_CONFIRMATION);
        repository.insertItem(caseId, context, command, AfterSaleStatus.WAITING_USER_CONFIRMATION);
        long sagaId = repository.insertSaga(caseId, context);
        repository.insertSagaStep(sagaId, "AFTERSALE_CREATED", "case-" + caseId + "-created");
        repository.outbox(caseId, context, UUID.randomUUID().toString(), "UserConfirmationRequired");
        repository.audit(caseId, context, "DRAFT_CREATED", command.commandId());
        return repository.findById(context.tenantId(), caseId).get();
    }

    @Override
    @Transactional
    public AfterSaleView confirm(TrustedAfterSaleContext context, Long caseId, String commandId, String idempotencyKey) {
        requireMember(context); AfterSaleView current = requireCase(context, caseId);
        if (current.status() != AfterSaleStatus.WAITING_USER_CONFIRMATION) throw new IllegalStateException("confirmation is not allowed");
        OmsOrder order = requireOrderOwnership(context, current.orderId());
        if (order.getStatus() != null && order.getStatus() == 0) {
            repository.updateStatus(caseId, AfterSaleStatus.EXECUTING);
            cancellations.cancel(new AfterSaleCase(String.valueOf(caseId), context.tenantId(), context.actorId(), order.getId(), current.requestedAmount(), false, false, current.ruleVersionId(), AfterSaleStatus.EXECUTING));
            repository.updateSaga(caseId, "RUNNING", "ORDER_CANCELLED");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "AfterSaleSubmitted");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "OrderCancelled");
            repository.audit(caseId, context, "ORDER_CANCELLED", commandId);
            return requireCase(context, caseId);
        }
        AfterSaleStatus target = current.requestedAmount().compareTo(SUPERVISOR_THRESHOLD) >= 0 ? AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL : AfterSaleStatus.WAITING_STAFF_APPROVAL;
        repository.updateStatus(caseId, target);
        repository.insertApproval(caseId, context, target == AfterSaleStatus.WAITING_STAFF_APPROVAL ? "STAFF_APPROVAL" : "SUPERVISOR_APPROVAL");
        repository.outbox(caseId, context, UUID.randomUUID().toString(), target == AfterSaleStatus.WAITING_STAFF_APPROVAL ? "StaffApprovalRequired" : "SupervisorApprovalRequired");
        repository.audit(caseId, context, "CONFIRMED", commandId);
        return requireCase(context, caseId);
    }

    @Override
    @Transactional
    public AfterSaleView approve(TrustedAfterSaleContext context, Long caseId, boolean approved, String commandId, String idempotencyKey) {
        if (!context.isStaff()) throw new SecurityException("staff approval required");
        AfterSaleView current = requireCase(context, caseId);
        if (current.status() != AfterSaleStatus.WAITING_STAFF_APPROVAL && current.status() != AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL) throw new IllegalStateException("approval is not allowed");
        AfterSaleStatus target = approved ? AfterSaleStatus.EXECUTING : AfterSaleStatus.REJECTED;
        repository.updateStatus(caseId, target);
        repository.decideApproval(caseId, context, current.status() == AfterSaleStatus.WAITING_STAFF_APPROVAL ? "STAFF_APPROVAL" : "SUPERVISOR_APPROVAL", approved ? "APPROVED" : "REJECTED");
        repository.updateSaga(caseId, approved ? "RUNNING" : "REJECTED", approved ? "REFUND_REQUESTED" : "REJECTED");
        repository.outbox(caseId, context, UUID.randomUUID().toString(), approved ? "AfterSaleApproved" : "AfterSaleRejected");
        repository.audit(caseId, context, approved ? "APPROVED" : "REJECTED", commandId);
        return requireCase(context, caseId);
    }

    @Override
    @Transactional
    public void consume(TrustedAfterSaleContext context, String eventId, String consumerName, Long caseId, String eventType) {
        AfterSaleView current = requireCase(context, caseId);
        if (!repository.claimInbox(eventId, consumerName, context.tenantId())) return;
        if ("RefundSucceeded".equals(eventType)) {
            repository.updateSaga(caseId, "RUNNING", "REFUND_SUCCEEDED");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "RefundSucceeded");
        } else if ("RefundFailed".equals(eventType)) {
            repository.updateStatus(caseId, AfterSaleStatus.RETRY_PENDING);
            repository.updateSaga(caseId, "RETRY_PENDING", "REFUND_FAILED");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "RefundFailed");
        } else if ("BenefitRollbackFailed".equals(eventType)) {
            repository.updateStatus(caseId, AfterSaleStatus.MANUAL_RECONCILIATION);
            repository.updateSaga(caseId, "MANUAL_RECONCILIATION", "MANUAL_RECONCILIATION");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "ManualReconciliationRequired");
        } else throw new IllegalArgumentException("unsupported saga event");
        repository.audit(caseId, context, eventType, null);
    }

    @Override
    @Transactional
    public void reportStep(TrustedAfterSaleContext context, Long caseId, SagaStepReport report) {
        if (!context.isStaff()) throw new SecurityException("service step report requires staff context");
        requireCase(context, caseId);
        if (!repository.claimInbox(report.eventId(), "p6-c-step-report", context.tenantId())) return;
        repository.ensureSagaStep(caseId, report.stepType(), report.idempotencyKey());
        if (!repository.updateSagaStep(caseId, report.stepType(), report.status(), report.idempotencyKey(), report.errorMessage())) throw new IllegalStateException("unknown or idempotency-mismatched saga step");
        if ("FAILED".equals(report.status())) {
            repository.updateStatus(caseId, AfterSaleStatus.RETRY_PENDING);
            repository.updateSaga(caseId, "RETRY_PENDING", report.stepType());
        } else if ("BENEFIT_ROLLBACK_FAILED".equals(report.status())) {
            repository.updateStatus(caseId, AfterSaleStatus.MANUAL_RECONCILIATION);
            repository.updateSaga(caseId, "MANUAL_RECONCILIATION", "MANUAL_RECONCILIATION");
            repository.outbox(caseId, context, UUID.randomUUID().toString(), "ManualReconciliationRequired");
        }
        repository.audit(caseId, context, "SAGA_STEP_" + report.status(), null);
    }

    @Override
    @Transactional
    public AfterSaleView resolveManualReconciliation(TrustedAfterSaleContext context, Long caseId, ManualReconciliationCommand command) {
        if (!context.isStaff()) throw new SecurityException("manual reconciliation requires staff context");
        AfterSaleView current = requireCase(context, caseId);
        if (!repository.claimInbox(command.commandId(), "manual-reconciliation", context.tenantId())) return current;
        if (current.status() != AfterSaleStatus.MANUAL_RECONCILIATION) throw new IllegalStateException("manual reconciliation is not pending");
        AfterSaleStatus target = "COMPLETED".equals(command.resolution()) ? AfterSaleStatus.COMPLETED : AfterSaleStatus.FAILED;
        repository.updateStatus(caseId, target);
        repository.updateSaga(caseId, target.name(), target.name());
        repository.outbox(caseId, context, UUID.randomUUID().toString(), target == AfterSaleStatus.COMPLETED ? "AfterSaleCompleted" : "ManualReconciliationRequired");
        repository.audit(caseId, context, "MANUAL_RECONCILIATION_" + target.name(), command.commandId());
        return requireCase(context, caseId);
    }

    @Override public Optional<AfterSaleView> find(TrustedAfterSaleContext context, Long caseId) { return repository.findById(context.tenantId(), caseId); }
    private AfterSaleView requireCase(TrustedAfterSaleContext context, Long caseId) { return find(context, caseId).orElseThrow(() -> new SecurityException("after-sale case not found")); }
    private void requireMember(TrustedAfterSaleContext context) { if (!context.isMember()) throw new SecurityException("member context required"); }
    private void verifyPayload(TrustedAfterSaleContext context, CreateAfterSaleDraft command) { if (!context.tenantId().equals(command.payloadTenantId()) || !context.actorId().equals(command.payloadMemberId())) throw new SecurityException("payload identity mismatch"); }
    private void verifyOrderOwnership(TrustedAfterSaleContext context, Long orderId) { requireOrderOwnership(context, orderId); }
    private OmsOrder requireOrderOwnership(TrustedAfterSaleContext context, Long orderId) {
        ResolvedOrderTenant resolved = orderTenants.resolve(orderId);
        OmsOrder order = orders.selectByPrimaryKey(orderId);
        if (resolved == null || !orderId.equals(resolved.orderId()) || !context.tenantId().equals(resolved.tenantId()) || order == null || !context.actorId().equals(order.getMemberId())) {
            throw new SecurityException("ORDER_TENANT_MISMATCH");
        }
        return order;
    }
}
