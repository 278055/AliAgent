package com.macro.mall.portal.internal.read;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderReturnApplyExample;
import com.macro.mall.model.PmsSkuStockExample;
import com.macro.mall.portal.internal.read.dto.InternalAfterSaleEligibilityView;
import com.macro.mall.portal.internal.read.dto.InternalLogisticsView;
import com.macro.mall.portal.internal.read.dto.InternalOrderView;
import com.macro.mall.portal.internal.read.dto.InternalProductView;
import com.macro.mall.portal.internal.read.dto.InternalStockView;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class InternalReadService {
    private final PmsProductMapper productMapper;
    private final PmsSkuStockMapper skuStockMapper;
    private final OmsOrderMapper orderMapper;
    private final OmsOrderReturnApplyMapper returnApplyMapper;
    private final StaffDataScopeAuthorizer staffDataScopeAuthorizer;

    public InternalReadService(PmsProductMapper productMapper, PmsSkuStockMapper skuStockMapper,
                               OmsOrderMapper orderMapper, OmsOrderReturnApplyMapper returnApplyMapper,
                               StaffDataScopeAuthorizer staffDataScopeAuthorizer) {
        this.productMapper = productMapper;
        this.skuStockMapper = skuStockMapper;
        this.orderMapper = orderMapper;
        this.returnApplyMapper = returnApplyMapper;
        this.staffDataScopeAuthorizer = staffDataScopeAuthorizer;
    }

    public InternalProductView getProduct(Long productId) {
        return InternalProductView.from(requireProduct(productId));
    }

    public List<InternalStockView> getStock(Long productId) {
        requireProduct(productId);
        PmsSkuStockExample example = new PmsSkuStockExample();
        example.createCriteria().andProductIdEqualTo(productId);
        return skuStockMapper.selectByExample(example).stream().map(InternalStockView::from).collect(Collectors.toList());
    }

    public InternalOrderView getOrder(Long orderId, UserSnapshot snapshot) {
        return InternalOrderView.from(authorizeOrder(orderId, snapshot));
    }

    public InternalLogisticsView getLogistics(Long orderId, UserSnapshot snapshot) {
        return InternalLogisticsView.from(authorizeOrder(orderId, snapshot));
    }

    public InternalAfterSaleEligibilityView getAfterSaleEligibility(Long orderId, UserSnapshot snapshot) {
        OmsOrder order = authorizeOrder(orderId, snapshot);
        if (order.getStatus() == null || order.getStatus() != 3) {
            return new InternalAfterSaleEligibilityView(orderId, false, "order is not completed");
        }
        OmsOrderReturnApplyExample example = new OmsOrderReturnApplyExample();
        example.createCriteria().andOrderIdEqualTo(orderId);
        if (!returnApplyMapper.selectByExample(example).isEmpty()) {
            return new InternalAfterSaleEligibilityView(orderId, false, "after-sale application already exists");
        }
        return new InternalAfterSaleEligibilityView(orderId, true, "eligible");
    }

    private OmsOrder authorizeOrder(Long orderId, UserSnapshot snapshot) {
        OmsOrder order = requireOrder(orderId);
        if (snapshot.getSubjectType() == SubjectType.MEMBER && snapshot.getSubjectId().equals(order.getMemberId())) {
            return order;
        }
        if (snapshot.getSubjectType() == SubjectType.STAFF) {
            staffDataScopeAuthorizer.checkOrderRead(snapshot);
            return order;
        }
        throw new InternalAccessDeniedException("order does not belong to current member");
    }

    private OmsOrder requireOrder(Long orderId) {
        OmsOrder order = orderMapper.selectByPrimaryKey(orderId);
        if (order == null) {
            throw new InternalAccessDeniedException("order not found");
        }
        return order;
    }

    private com.macro.mall.model.PmsProduct requireProduct(Long productId) {
        com.macro.mall.model.PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null) {
            throw new InternalAccessDeniedException("product not found");
        }
        return product;
    }
}
