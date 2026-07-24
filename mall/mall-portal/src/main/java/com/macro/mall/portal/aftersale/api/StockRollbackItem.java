package com.macro.mall.portal.aftersale.api;

/** 由售后事务从订单项事实解析出的库存回滚项。 */
public final class StockRollbackItem {
    private final Long orderItemId;
    private final Long productSkuId;
    private final Integer quantity;

    public StockRollbackItem(Long orderItemId, Long productSkuId, Integer quantity) {
        this.orderItemId = orderItemId;
        this.productSkuId = productSkuId;
        this.quantity = quantity;
    }

    public Long orderItemId() { return orderItemId; }
    public Long productSkuId() { return productSkuId; }
    public Integer quantity() { return quantity; }
}
