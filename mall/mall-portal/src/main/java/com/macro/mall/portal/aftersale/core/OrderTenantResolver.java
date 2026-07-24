package com.macro.mall.portal.aftersale.core;

/** 集成层唯一可用的订单租户权威查询端口。 */
public interface OrderTenantResolver {
    ResolvedOrderTenant resolve(Long orderId);
}
