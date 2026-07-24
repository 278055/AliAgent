package com.macro.mall.portal.aftersale.api;

import com.macro.mall.portal.aftersale.core.AfterSaleStatus;
import java.math.BigDecimal;

public final class AfterSaleView {
    private final Long caseId; private final Long orderId; private final String caseNo; private final String ruleVersionId; private final AfterSaleStatus status; private final BigDecimal requestedAmount;
    public AfterSaleView(Long caseId, Long orderId, String caseNo, String ruleVersionId, AfterSaleStatus status, BigDecimal requestedAmount) { this.caseId = caseId; this.orderId = orderId; this.caseNo = caseNo; this.ruleVersionId = ruleVersionId; this.status = status; this.requestedAmount = requestedAmount; }
    public Long caseId() { return caseId; } public Long orderId() { return orderId; } public String caseNo() { return caseNo; } public String ruleVersionId() { return ruleVersionId; } public AfterSaleStatus status() { return status; } public BigDecimal requestedAmount() { return requestedAmount; }
}
