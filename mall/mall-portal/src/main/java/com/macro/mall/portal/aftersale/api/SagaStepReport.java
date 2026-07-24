package com.macro.mall.portal.aftersale.api;

public final class SagaStepReport {
    private final String eventId; private final String idempotencyKey; private final String stepType; private final String status; private final String errorMessage;
    public SagaStepReport(String eventId, String idempotencyKey, String stepType, String status, String errorMessage) { this.eventId = eventId; this.idempotencyKey = idempotencyKey; this.stepType = stepType; this.status = status; this.errorMessage = errorMessage; }
    public String eventId() { return eventId; } public String idempotencyKey() { return idempotencyKey; } public String stepType() { return stepType; } public String status() { return status; } public String errorMessage() { return errorMessage; }
}
