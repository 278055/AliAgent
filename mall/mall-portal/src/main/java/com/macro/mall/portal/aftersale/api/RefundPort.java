package com.macro.mall.portal.aftersale.api;

public interface RefundPort {
    RefundResult refund(RefundCommand command);
    RefundResult query(String refundRequestId);
}
