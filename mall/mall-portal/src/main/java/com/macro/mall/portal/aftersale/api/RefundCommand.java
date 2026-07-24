package com.macro.mall.portal.aftersale.api;

import java.math.BigDecimal;

public final class RefundCommand {
    private final Long caseId; private final String tenantId; private final String refundRequestId; private final BigDecimal amount;
    public RefundCommand(Long caseId, String tenantId, String refundRequestId, BigDecimal amount) { this.caseId = caseId; this.tenantId = tenantId; this.refundRequestId = refundRequestId; this.amount = amount; }
    public Long caseId() { return caseId; } public String tenantId() { return tenantId; } public String refundRequestId() { return refundRequestId; } public BigDecimal amount() { return amount; }
}
