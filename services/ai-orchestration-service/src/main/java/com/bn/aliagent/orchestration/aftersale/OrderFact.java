package com.bn.aliagent.orchestration.aftersale;

import java.math.BigDecimal;
import java.util.Map;

public record OrderFact(String orderId, String tenantId, String actorId, boolean paid, BigDecimal amount,
                        Map<String, OrderItemFact> items) {
    public OrderFact withAmount(BigDecimal value) {
        return new OrderFact(orderId, tenantId, actorId, paid, value, items);
    }
}
