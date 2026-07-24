package com.macro.mall.portal.aftersale.api;

import java.util.List;

public final class BenefitRollbackCommand {
    private final Long caseId; private final String tenantId; private final String idempotencyKey;
    private final Long orderId; private final Long memberId; private final List<StockRollbackItem> stockItems;
    private final Long couponHistoryId; private final Integer usedIntegration;
    public BenefitRollbackCommand(Long caseId, String tenantId, String idempotencyKey, Long orderId, Long memberId,
                                  List<StockRollbackItem> stockItems, Long couponHistoryId, Integer usedIntegration) {
        this.caseId = caseId; this.tenantId = tenantId; this.idempotencyKey = idempotencyKey;
        this.orderId = orderId; this.memberId = memberId; this.stockItems = List.copyOf(stockItems);
        this.couponHistoryId = couponHistoryId; this.usedIntegration = usedIntegration;
    }
    public Long caseId() { return caseId; } public String tenantId() { return tenantId; } public String idempotencyKey() { return idempotencyKey; }
    public Long orderId() { return orderId; } public Long memberId() { return memberId; } public List<StockRollbackItem> stockItems() { return stockItems; }
    public Long couponHistoryId() { return couponHistoryId; } public Integer usedIntegration() { return usedIntegration; }
}
