package com.macro.mall.portal.aftersale.core;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.springframework.stereotype.Component;

/** 仅编排校验和既有取消服务，库存、券和积分回滚仍由既有服务负责。 */
@Component
public final class PortalOrderCancellationAdapter implements OrderCancellationPort {
    private final OmsOrderMapper orderMapper;
    private final OmsPortalOrderService portalOrderService;
    private final OrderTenantResolver orderTenants;

    public PortalOrderCancellationAdapter(OmsOrderMapper orderMapper, OmsPortalOrderService portalOrderService, OrderTenantResolver orderTenants) {
        this.orderMapper = orderMapper;
        this.portalOrderService = portalOrderService;
        this.orderTenants = orderTenants;
    }

    @Override
    public void cancel(AfterSaleCase afterSaleCase) {
        if (afterSaleCase.paid() || afterSaleCase.status() != AfterSaleStatus.EXECUTING) throw new IllegalStateException("order cancellation is not eligible");
        OmsOrder before = requireEligible(afterSaleCase);
        portalOrderService.cancelOrder(before.getId());
        OmsOrder after = orderMapper.selectByPrimaryKey(before.getId());
        if (after == null || after.getStatus() == null || after.getStatus() != 4) {
            throw new IllegalStateException("existing order cancellation did not close order");
        }
    }

    private OmsOrder requireEligible(AfterSaleCase afterSaleCase) {
        OmsOrder order = orderMapper.selectByPrimaryKey(afterSaleCase.orderId());
        ResolvedOrderTenant tenant = orderTenants.resolve(afterSaleCase.orderId());
        if (tenant == null || !afterSaleCase.tenantId().equals(tenant.tenantId()) || order == null || !afterSaleCase.memberId().equals(order.getMemberId()) || order.getStatus() == null || order.getStatus() != 0
                || order.getDeleteStatus() == null || order.getDeleteStatus() != 0) {
            throw new SecurityException("ORDER_TENANT_MISMATCH");
        }
        return order;
    }
}
