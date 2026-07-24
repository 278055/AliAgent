package com.macro.mall.portal.aftersale.api;

/** 由 mall 持有步骤幂等与事务边界，P6-C 只能通过此端口执行权益回滚。 */
public interface BenefitRollbackExecutionPort {
    StepResult execute(TrustedAfterSaleContext context, Long caseId, String stepType, String idempotencyKey);
}
