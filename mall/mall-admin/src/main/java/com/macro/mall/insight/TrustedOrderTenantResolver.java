package com.macro.mall.insight;

/** 订单租户仅能从订单归属绑定读取，不能来自后台请求。 */
public interface TrustedOrderTenantResolver {
    String requireTenantId(Long orderId);
}
