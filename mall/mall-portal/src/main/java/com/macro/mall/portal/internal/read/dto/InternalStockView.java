package com.macro.mall.portal.internal.read.dto;

import com.macro.mall.model.PmsSkuStock;

public class InternalStockView {
    private final String skuCode;
    private final Integer availableStock;

    private InternalStockView(String skuCode, Integer availableStock) {
        this.skuCode = skuCode;
        this.availableStock = availableStock;
    }

    public static InternalStockView from(PmsSkuStock stock) {
        int total = stock.getStock() == null ? 0 : stock.getStock();
        int locked = stock.getLockStock() == null ? 0 : stock.getLockStock();
        return new InternalStockView(stock.getSkuCode(), Math.max(0, total - locked));
    }

    public String getSkuCode() { return skuCode; }
    public Integer getAvailableStock() { return availableStock; }
}
