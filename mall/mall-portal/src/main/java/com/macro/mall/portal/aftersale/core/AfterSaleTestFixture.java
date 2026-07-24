package com.macro.mall.portal.aftersale.core;

import com.macro.mall.portal.aftersale.persistence.InMemoryAfterSaleStore;

import java.math.BigDecimal;

public final class AfterSaleTestFixture {
    private AfterSaleTestFixture() { }
    public static AfterSaleService service() { return new AfterSaleService(new InMemoryAfterSaleStore(), command -> "test-rules-v1", value -> { }); }
    public static AfterSaleCommand command(String commandId, String key, Long tenant, Long member, Long order, BigDecimal amount, boolean risk) {
        return command(commandId, key, tenant, member, order, amount, risk, false);
    }
    public static AfterSaleCommand command(String commandId, String key, Long tenant, Long member, Long order, BigDecimal amount, boolean risk, boolean paid) {
        return new AfterSaleCommand(commandId, key, tenant.equals(1L) ? "test-tenant" : "other-tenant", member, order, amount, risk, paid);
    }
}
