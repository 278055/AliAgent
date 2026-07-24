package com.bn.aliagent.orchestration.aftersale;

public interface AfterSaleMallPort {
    OrderFact findOrder(TrustedAfterSaleContext context, String orderId);
    AfterSaleStatus submit(TrustedAfterSaleContext context, ControlledAfterSaleCommand command);
    AfterSaleStatus queryCommand(TrustedAfterSaleContext context, String idempotencyKey);
    AfterSaleStatus queryCase(TrustedAfterSaleContext context, String caseId);
}
