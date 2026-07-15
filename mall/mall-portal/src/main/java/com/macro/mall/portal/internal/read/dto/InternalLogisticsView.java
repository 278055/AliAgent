package com.macro.mall.portal.internal.read.dto;

import com.macro.mall.model.OmsOrder;

import java.util.Date;

public class InternalLogisticsView {
    private final String deliveryCompany;
    private final String deliverySn;
    private final Date deliveryTime;
    private final Integer orderStatus;

    private InternalLogisticsView(String deliveryCompany, String deliverySn, Date deliveryTime, Integer orderStatus) {
        this.deliveryCompany = deliveryCompany;
        this.deliverySn = deliverySn;
        this.deliveryTime = deliveryTime;
        this.orderStatus = orderStatus;
    }

    public static InternalLogisticsView from(OmsOrder order) {
        return new InternalLogisticsView(order.getDeliveryCompany(), order.getDeliverySn(), order.getDeliveryTime(), order.getStatus());
    }

    public String getDeliveryCompany() { return deliveryCompany; }
    public String getDeliverySn() { return deliverySn; }
    public Date getDeliveryTime() { return deliveryTime; }
    public Integer getOrderStatus() { return orderStatus; }
}
