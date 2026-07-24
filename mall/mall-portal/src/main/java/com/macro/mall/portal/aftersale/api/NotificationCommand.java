package com.macro.mall.portal.aftersale.api;

public final class NotificationCommand {
    private final Long caseId; private final String tenantId; private final String idempotencyKey; private final String eventType;
    public NotificationCommand(Long caseId, String tenantId, String idempotencyKey, String eventType) { this.caseId = caseId; this.tenantId = tenantId; this.idempotencyKey = idempotencyKey; this.eventType = eventType; }
    public Long caseId() { return caseId; } public String tenantId() { return tenantId; } public String idempotencyKey() { return idempotencyKey; } public String eventType() { return eventType; }
}
