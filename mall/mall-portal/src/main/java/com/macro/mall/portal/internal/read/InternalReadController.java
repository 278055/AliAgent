package com.macro.mall.portal.internal.read;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.internal.read.dto.InternalAfterSaleEligibilityView;
import com.macro.mall.portal.internal.read.dto.InternalLogisticsView;
import com.macro.mall.portal.internal.read.dto.InternalOrderView;
import com.macro.mall.portal.internal.read.dto.InternalProductView;
import com.macro.mall.portal.internal.read.dto.InternalStockView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/mall")
public class InternalReadController {
    private final InternalReadService internalReadService;

    public InternalReadController(InternalReadService internalReadService) {
        this.internalReadService = internalReadService;
    }

    @GetMapping("/products/{productId}")
    public CommonResult<InternalProductView> getProduct(@PathVariable Long productId) {
        return CommonResult.success(internalReadService.getProduct(productId));
    }

    @GetMapping("/products/{productId}/stock")
    public CommonResult<List<InternalStockView>> getStock(@PathVariable Long productId) {
        return CommonResult.success(internalReadService.getStock(productId));
    }

    @GetMapping("/orders/{orderId}")
    public CommonResult<InternalOrderView> getOrder(@PathVariable Long orderId,
            @RequestAttribute("com.macro.mall.portal.internal.read.UserSnapshot") UserSnapshot snapshot) {
        return CommonResult.success(internalReadService.getOrder(orderId, snapshot));
    }

    @GetMapping("/orders/{orderId}/logistics")
    public CommonResult<InternalLogisticsView> getLogistics(@PathVariable Long orderId,
            @RequestAttribute("com.macro.mall.portal.internal.read.UserSnapshot") UserSnapshot snapshot) {
        return CommonResult.success(internalReadService.getLogistics(orderId, snapshot));
    }

    @GetMapping("/orders/{orderId}/after-sale-eligibility")
    public CommonResult<InternalAfterSaleEligibilityView> getAfterSaleEligibility(@PathVariable Long orderId,
            @RequestAttribute("com.macro.mall.portal.internal.read.UserSnapshot") UserSnapshot snapshot) {
        return CommonResult.success(internalReadService.getAfterSaleEligibility(orderId, snapshot));
    }

}
