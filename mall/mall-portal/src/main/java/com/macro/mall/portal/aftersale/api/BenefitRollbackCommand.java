package com.macro.mall.portal.aftersale.api;

public final class BenefitRollbackCommand {
    private final Long caseId; private final String tenantId; private final String idempotencyKey;
    public BenefitRollbackCommand(Long caseId, String tenantId, String idempotencyKey) { this.caseId = caseId; this.tenantId = tenantId; this.idempotencyKey = idempotencyKey; }
    public Long caseId() { return caseId; } public String tenantId() { return tenantId; } public String idempotencyKey() { return idempotencyKey; }
}
