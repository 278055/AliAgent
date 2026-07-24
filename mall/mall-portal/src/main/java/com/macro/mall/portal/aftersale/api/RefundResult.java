package com.macro.mall.portal.aftersale.api;

public final class RefundResult {
    private final String refundRequestId; private final String status; private final String detail;
    public RefundResult(String refundRequestId, String status, String detail) { this.refundRequestId = refundRequestId; this.status = status; this.detail = detail; }
    public String refundRequestId() { return refundRequestId; } public String status() { return status; } public String detail() { return detail; }
}
