package com.macro.mall.portal.aftersale.core;

import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.BenefitRollbackCommand;
import com.macro.mall.portal.aftersale.api.BenefitRollbackExecutionPort;
import com.macro.mall.portal.aftersale.api.BenefitRollbackPort;
import com.macro.mall.portal.aftersale.api.StepResult;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import com.macro.mall.portal.aftersale.persistence.AfterSaleJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 在同一 MySQL 事务内先占有步骤幂等键，再执行实际权益更新并落步骤和 Outbox 事实。 */
@Service
public final class AtomicBenefitRollbackExecutionService implements BenefitRollbackExecutionPort {
    private final AfterSaleJdbcRepository repository;
    private final BenefitRollbackPort benefits;

    public AtomicBenefitRollbackExecutionService(AfterSaleJdbcRepository repository, BenefitRollbackPort benefits) {
        this.repository = repository;
        this.benefits = benefits;
    }

    @Override
    @Transactional
    public StepResult execute(TrustedAfterSaleContext context, Long caseId, String stepType, String idempotencyKey) {
        if (!context.isStaff()) throw new SecurityException("service benefit execution requires staff context");
        AfterSaleView afterSaleCase = repository.findById(context.tenantId(), caseId).orElseThrow(() -> new SecurityException("after-sale case not found"));
        if (afterSaleCase.status() != AfterSaleStatus.EXECUTING && afterSaleCase.status() != AfterSaleStatus.RETRY_PENDING) throw new IllegalStateException("benefit rollback is not executable");
        if (!repository.claimSagaStep(caseId, stepType, idempotencyKey)) return new StepResult(repository.sagaStepStatus(caseId, stepType, idempotencyKey), "idempotent replay");
        try {
            StepResult result = invoke(stepType, new BenefitRollbackCommand(caseId, context.tenantId(), idempotencyKey));
            if (!"SUCCEEDED".equals(result.status())) return failed(context, caseId, stepType, idempotencyKey, result.detail());
            repository.updateSagaStep(caseId, stepType, "SUCCEEDED", idempotencyKey, null);
            repository.updateSaga(caseId, "RUNNING", stepType);
            repository.audit(caseId, context, "BENEFIT_" + stepType + "_SUCCEEDED", null);
            return result;
        } catch (RuntimeException exception) {
            return failed(context, caseId, stepType, idempotencyKey, "benefit rollback failed");
        }
    }

    private StepResult invoke(String stepType, BenefitRollbackCommand command) {
        if ("STOCK_RESTORED".equals(stepType)) return benefits.restoreStock(command);
        if ("COUPON_RESTORED".equals(stepType)) return benefits.restoreCoupon(command);
        if ("POINTS_RESTORED".equals(stepType)) return benefits.restorePoints(command);
        throw new IllegalArgumentException("unsupported benefit step");
    }

    private StepResult failed(TrustedAfterSaleContext context, Long caseId, String stepType, String idempotencyKey, String detail) {
        repository.updateSagaStep(caseId, stepType, "FAILED", idempotencyKey, detail);
        repository.updateStatus(caseId, AfterSaleStatus.MANUAL_RECONCILIATION);
        repository.updateSaga(caseId, "MANUAL_RECONCILIATION", "MANUAL_RECONCILIATION");
        // 必须保留退款成功事实，不执行任何反向退款；事件按冻结顺序写入同一 Outbox 事务。
        repository.outbox(caseId, context, UUID.randomUUID().toString(), "BenefitRollbackFailed");
        repository.outbox(caseId, context, UUID.randomUUID().toString(), "ManualReconciliationRequired");
        repository.audit(caseId, context, "BENEFIT_" + stepType + "_FAILED", null);
        return new StepResult("FAILED", detail);
    }
}
