package com.macro.mall.portal.internal.read.dto;

import com.macro.mall.model.PmsProduct;

import java.math.BigDecimal;

public class InternalProductView {
    private final Long id;
    private final String name;
    private final String subTitle;
    private final String pic;
    private final BigDecimal price;
    private final Integer publishStatus;

    private InternalProductView(Long id, String name, String subTitle, String pic, BigDecimal price, Integer publishStatus) {
        this.id = id;
        this.name = name;
        this.subTitle = subTitle;
        this.pic = pic;
        this.price = price;
        this.publishStatus = publishStatus;
    }

    public static InternalProductView from(PmsProduct product) {
        return new InternalProductView(product.getId(), product.getName(), product.getSubTitle(), product.getPic(),
                product.getPrice(), product.getPublishStatus());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSubTitle() { return subTitle; }
    public String getPic() { return pic; }
    public BigDecimal getPrice() { return price; }
    public Integer getPublishStatus() { return publishStatus; }
}
