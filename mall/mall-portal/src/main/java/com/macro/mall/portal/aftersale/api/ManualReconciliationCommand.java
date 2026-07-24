package com.macro.mall.portal.aftersale.api;

public final class ManualReconciliationCommand {
    private final String commandId; private final String idempotencyKey; private final String resolution;
    public ManualReconciliationCommand(String commandId, String idempotencyKey, String resolution) { this.commandId = commandId; this.idempotencyKey = idempotencyKey; this.resolution = resolution; }
    public String commandId() { return commandId; } public String idempotencyKey() { return idempotencyKey; } public String resolution() { return resolution; }
}
