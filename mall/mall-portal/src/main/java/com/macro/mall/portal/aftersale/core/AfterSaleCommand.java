package com.macro.mall.portal.aftersale.core;

import java.math.BigDecimal;

public final class AfterSaleCommand {
    private final String commandId; private final String idempotencyKey; private final String tenantId; private final Long actorId;
    private final Long orderId; private final BigDecimal amount; private final boolean highRisk; private final boolean paid;
    public AfterSaleCommand(String commandId, String idempotencyKey, String tenantId, Long actorId, Long orderId, BigDecimal amount, boolean highRisk, boolean paid) {
        this.commandId = commandId; this.idempotencyKey = idempotencyKey; this.tenantId = tenantId; this.actorId = actorId;
        this.orderId = orderId; this.amount = amount; this.highRisk = highRisk; this.paid = paid;
    }
    public String commandId() { return commandId; } public String idempotencyKey() { return idempotencyKey; } public String tenantId() { return tenantId; }
    public Long actorId() { return actorId; } public Long orderId() { return orderId; } public BigDecimal amount() { return amount; }
    public boolean highRisk() { return highRisk; } public boolean paid() { return paid; }
}
