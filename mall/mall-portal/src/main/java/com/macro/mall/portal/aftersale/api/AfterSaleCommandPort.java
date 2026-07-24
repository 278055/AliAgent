package com.macro.mall.portal.aftersale.api;

public interface AfterSaleCommandPort {
    AfterSaleView createDraft(TrustedAfterSaleContext context, CreateAfterSaleDraft command);
    AfterSaleView confirm(TrustedAfterSaleContext context, Long caseId, String commandId, String idempotencyKey);
    AfterSaleView approve(TrustedAfterSaleContext context, Long caseId, boolean approved, String commandId, String idempotencyKey);
    void consume(TrustedAfterSaleContext context, String eventId, String consumerName, Long caseId, String eventType);
    void reportStep(TrustedAfterSaleContext context, Long caseId, SagaStepReport report);
    AfterSaleView resolveManualReconciliation(TrustedAfterSaleContext context, Long caseId, ManualReconciliationCommand command);
}
