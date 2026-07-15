package com.macro.mall.portal.internal.read.dto;

public class InternalAfterSaleEligibilityView {
    private final Long orderId;
    private final boolean eligible;
    private final String reason;

    public InternalAfterSaleEligibilityView(Long orderId, boolean eligible, String reason) {
        this.orderId = orderId;
        this.eligible = eligible;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public boolean isEligible() { return eligible; }
    public String getReason() { return reason; }
}
