package com.macro.mall.portal.internal.read;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.internal.read.dto.InternalOrderView;
import com.macro.mall.portal.internal.read.dto.InternalProductView;
import com.macro.mall.portal.internal.read.dto.InternalStockView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalReadServiceTest {

    @Test
    void memberCannotReadAnotherMembersOrder() {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        OmsOrder order = new OmsOrder();
        order.setId(100L);
        order.setMemberId(200L);
        when(orderMapper.selectByPrimaryKey(100L)).thenReturn(order);

        InternalReadService service = new InternalReadService(mock(PmsProductMapper.class), mock(PmsSkuStockMapper.class),
                orderMapper, mock(OmsOrderReturnApplyMapper.class), new RoleBasedStaffDataScopeAuthorizer());

        assertThrows(InternalAccessDeniedException.class,
                () -> service.getOrder(100L, UserSnapshot.member(201L)));
    }

    @Test
    void orderViewDoesNotExposePaymentOrReceiverInformation() {
        OmsOrder order = new OmsOrder();
        order.setId(100L);
        order.setMemberId(200L);
        order.setOrderSn("test-order-100");
        order.setStatus(2);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setPayAmount(new BigDecimal("88.00"));
        order.setPayType(1);
        order.setReceiverName("Alice");
        order.setReceiverPhone("13800000000");
        order.setReceiverDetailAddress("secret address");

        InternalOrderView view = InternalOrderView.from(order);

        assertEquals("test-order-100", view.getOrderSn());
        assertFalse(Arrays.asList(view.getClass().getDeclaredFields()).stream()
                .map(field -> field.getName())
                .anyMatch(name -> name.contains("pay") || name.contains("receiver") || name.contains("address")));
    }

    @Test
    void productAndStockViewsExposeOnlyReadSafeFields() {
        PmsProduct product = new PmsProduct();
        product.setId(1L);
        product.setName("test-product");
        product.setPrice(new BigDecimal("12.00"));
        product.setStock(9);
        PmsSkuStock sku = new PmsSkuStock();
        sku.setSkuCode("sku-1");
        sku.setStock(5);
        sku.setLockStock(3);

        assertEquals("test-product", InternalProductView.from(product).getName());
        assertEquals(2, InternalStockView.from(sku).getAvailableStock());
    }

    @Test
    void staffWithoutOrderReadAllScopeCannotReadOrder() {
        OmsOrderMapper orderMapper = mock(OmsOrderMapper.class);
        OmsOrder order = new OmsOrder();
        order.setId(100L);
        order.setMemberId(200L);
        when(orderMapper.selectByPrimaryKey(100L)).thenReturn(order);
        InternalReadService service = new InternalReadService(mock(PmsProductMapper.class), mock(PmsSkuStockMapper.class),
                orderMapper, mock(OmsOrderReturnApplyMapper.class), new RoleBasedStaffDataScopeAuthorizer());
        UserSnapshot staff = new UserSnapshot("test-tenant", 1L, SubjectType.STAFF, Collections.emptySet());

        assertThrows(InternalAccessDeniedException.class, () -> service.getLogistics(100L, staff));
    }
}
