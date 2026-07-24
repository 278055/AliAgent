package com.macro.mall.portal.aftersale.api;

import java.math.BigDecimal;

public final class CreateAfterSaleDraft {
    private final String commandId; private final String commandType; private final String idempotencyKey; private final Long orderId; private final Long orderItemId;
    private final Long productId; private final Integer quantity; private final BigDecimal requestedAmount; private final String payloadTenantId; private final Long payloadMemberId;
    public CreateAfterSaleDraft(String commandId, String commandType, String idempotencyKey, Long orderId, Long orderItemId, Long productId, Integer quantity, BigDecimal requestedAmount, String payloadTenantId, Long payloadMemberId) {
        this.commandId = commandId; this.commandType = commandType; this.idempotencyKey = idempotencyKey; this.orderId = orderId; this.orderItemId = orderItemId;
        this.productId = productId; this.quantity = quantity; this.requestedAmount = requestedAmount; this.payloadTenantId = payloadTenantId; this.payloadMemberId = payloadMemberId;
    }
    public String commandId() { return commandId; } public String commandType() { return commandType; } public String idempotencyKey() { return idempotencyKey; }
    public Long orderId() { return orderId; } public Long orderItemId() { return orderItemId; } public Long productId() { return productId; } public Integer quantity() { return quantity; }
    public BigDecimal requestedAmount() { return requestedAmount; } public String payloadTenantId() { return payloadTenantId; } public Long payloadMemberId() { return payloadMemberId; }
}
