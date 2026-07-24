package com.macro.mall.portal.aftersale.core;

import java.math.BigDecimal;

public final class AfterSaleCase {
    private final String caseId; private final String tenantId; private final Long memberId; private final Long orderId;
    private final BigDecimal amount; private final boolean paid; private final boolean highRisk; private final String ruleVersionId; private final AfterSaleStatus status;
    public AfterSaleCase(String caseId, String tenantId, Long memberId, Long orderId, BigDecimal amount, boolean paid, boolean highRisk, String ruleVersionId, AfterSaleStatus status) {
        this.caseId = caseId; this.tenantId = tenantId; this.memberId = memberId; this.orderId = orderId; this.amount = amount;
        this.paid = paid; this.highRisk = highRisk; this.ruleVersionId = ruleVersionId; this.status = status;
    }
    public String caseId() { return caseId; } public String tenantId() { return tenantId; } public Long memberId() { return memberId; }
    public Long orderId() { return orderId; } public BigDecimal amount() { return amount; } public boolean paid() { return paid; }
    public boolean highRisk() { return highRisk; } public String ruleVersionId() { return ruleVersionId; } public AfterSaleStatus status() { return status; }
}
