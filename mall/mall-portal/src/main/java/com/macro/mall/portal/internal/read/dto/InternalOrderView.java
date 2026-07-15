package com.macro.mall.portal.internal.read.dto;

import com.macro.mall.model.OmsOrder;

import java.math.BigDecimal;
import java.util.Date;

public class InternalOrderView {
    private final Long id;
    private final String orderSn;
    private final Integer status;
    private final BigDecimal totalAmount;
    private final Date createTime;

    private InternalOrderView(Long id, String orderSn, Integer status, BigDecimal totalAmount, Date createTime) {
        this.id = id;
        this.orderSn = orderSn;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createTime = createTime;
    }

    public static InternalOrderView from(OmsOrder order) {
        return new InternalOrderView(order.getId(), order.getOrderSn(), order.getStatus(), order.getTotalAmount(), order.getCreateTime());
    }

    public Long getId() { return id; }
    public String getOrderSn() { return orderSn; }
    public Integer getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Date getCreateTime() { return createTime; }
}
