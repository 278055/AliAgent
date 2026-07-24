package com.macro.mall.portal.aftersale;

import com.macro.mall.portal.aftersale.api.BenefitRollbackCommand;
import com.macro.mall.portal.aftersale.api.BenefitRollbackPort;
import com.macro.mall.portal.aftersale.api.RefundCommand;
import com.macro.mall.portal.aftersale.api.StepResult;
import com.macro.mall.portal.aftersale.api.StockRollbackItem;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import com.macro.mall.portal.aftersale.benefit.JdbcBenefitRollbackAdapter;
import com.macro.mall.portal.aftersale.core.AfterSaleStatus;
import com.macro.mall.portal.aftersale.core.AtomicBenefitRollbackExecutionService;
import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.persistence.AfterSaleJdbcRepository;
import com.macro.mall.portal.aftersale.refund.MockRefundAdapter;
import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P6CRefundBenefitTest {
    @Test void mock退款支持成功失败超时查询和请求号幂等() {
        MockRefundAdapter adapter = new MockRefundAdapter();
        assertEquals("SUCCEEDED", adapter.refund(refund("test-refund-success", "10.01")).status());
        assertEquals("FAILED", adapter.refund(refund("test-refund-failed", "1.00")).status());
        assertEquals("UNKNOWN", adapter.refund(refund("test-refund-timeout", "1.00")).status());
        assertEquals("PROCESSING", adapter.refund(refund("test-refund-processing", "1.00")).status());
        assertEquals("PROCESSING", adapter.query("test-refund-timeout").status());
        adapter.refund(refund("test-refund-timeout-success", "1.00"));
        assertEquals("SUCCEEDED", adapter.query("test-refund-timeout-success").status());
        assertEquals("mock-ref-test-refund-timeout-success", adapter.query("test-refund-timeout-success").detail());
        adapter.refund(refund("test-refund-timeout-not-refunded", "1.00"));
        assertEquals("FAILED", adapter.query("test-refund-timeout-not-refunded").status());
        assertEquals("MOCK_NOT_REFUNDED", adapter.query("test-refund-timeout-not-refunded").detail());
        assertEquals("SUCCEEDED", adapter.refund(refund("test-refund-success", "10.010")).status());
        assertEquals(6, adapter.factCount());
    }

    @Test void mock退款保存完整事实且不使用浮点金额或真实渠道引用() throws Exception {
        MockRefundAdapter adapter = new MockRefundAdapter();
        adapter.refund(refund("test-refund-fact", "10.01"));

        MockRefundAdapter.RefundFactView fact = adapter.fact("test-refund-fact");
        assertNotNull(fact.requestedAt());
        assertEquals(new BigDecimal("10.01"), fact.amount());
        assertEquals("SUCCEEDED", fact.status());
        assertEquals("mock-ref-test-refund-fact", fact.channelReference());
        assertNull(fact.failureCategory());
        assertNull(fact.lastQueriedAt());
        adapter.query("test-refund-fact");
        assertNotNull(adapter.fact("test-refund-fact").lastQueriedAt());
        for (Field field : MockRefundAdapter.class.getDeclaredFields()) {
            assertFalse(field.getType() == float.class || field.getType() == double.class);
        }
    }

    @Test void 库存回滚使用数据库原子增量并支持多订单项() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT id FROM pms_sku_stock WHERE id IN (?,?) FOR UPDATE", Long.class, 20L, 21L)).thenReturn(List.of(20L, 21L));
        when(jdbc.update("UPDATE pms_sku_stock SET stock=stock+CASE id WHEN ? THEN ? WHEN ? THEN ? ELSE 0 END WHERE id IN (?,?)", 20L, 2, 21L, 3, 20L, 21L)).thenReturn(2);
        JdbcBenefitRollbackAdapter adapter = new JdbcBenefitRollbackAdapter(jdbc);

        assertEquals("SUCCEEDED", adapter.restoreStock(command(List.of(new StockRollbackItem(5L, 20L, 2), new StockRollbackItem(6L, 21L, 3)), null, 0)).status());
        verify(jdbc).queryForList("SELECT id FROM pms_sku_stock WHERE id IN (?,?) FOR UPDATE", Long.class, 20L, 21L);
        verify(jdbc).update("UPDATE pms_sku_stock SET stock=stock+CASE id WHEN ? THEN ? WHEN ? THEN ? ELSE 0 END WHERE id IN (?,?)", 20L, 2, 21L, 3, 20L, 21L);
    }

    @Test void 同一SKU的多个订单项合并数量后只更新一次() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT id FROM pms_sku_stock WHERE id IN (?) FOR UPDATE", Long.class, 20L)).thenReturn(List.of(20L));
        when(jdbc.update("UPDATE pms_sku_stock SET stock=stock+CASE id WHEN ? THEN ? ELSE 0 END WHERE id IN (?)", 20L, 5, 20L)).thenReturn(1);
        JdbcBenefitRollbackAdapter adapter = new JdbcBenefitRollbackAdapter(jdbc);

        assertEquals("SUCCEEDED", adapter.restoreStock(command(List.of(
                new StockRollbackItem(5L, 20L, 2), new StockRollbackItem(6L, 20L, 3)), null, 0)).status());
        verify(jdbc).update("UPDATE pms_sku_stock SET stock=stock+CASE id WHEN ? THEN ? ELSE 0 END WHERE id IN (?)", 20L, 5, 20L);
    }

    @Test void 优惠券和积分按可信事实原子恢复且空权益无写入() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE sms_coupon_history SET use_status=0,use_time=NULL,order_id=NULL,order_sn=NULL WHERE id=? AND member_id=? AND order_id=?", 7L, 10L, 100L)).thenReturn(1);
        when(jdbc.update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?", 100, 10L)).thenReturn(1);
        JdbcBenefitRollbackAdapter adapter = new JdbcBenefitRollbackAdapter(jdbc);

        assertEquals("SUCCEEDED", adapter.restoreCoupon(command(List.of(), 7L, 100)).status());
        assertEquals("SUCCEEDED", adapter.restorePoints(command(List.of(), 7L, 100)).status());
        assertEquals("SUCCEEDED", adapter.restoreCoupon(command(List.of(), null, 0)).status());
        assertEquals("SUCCEEDED", adapter.restorePoints(command(List.of(), null, 0)).status());
        verify(jdbc).update("UPDATE sms_coupon_history SET use_status=0,use_time=NULL,order_id=NULL,order_sn=NULL WHERE id=? AND member_id=? AND order_id=?", 7L, 10L, 100L);
        verify(jdbc).update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?", 100, 10L);
        verify(jdbc, never()).update("UPDATE ums_member SET integration=COALESCE(integration,0)+? WHERE id=?", 0, 10L);
    }

    @Test void 三类权益成功步骤按稳定业务键回放且不重复写入() {
        assertIdempotentBenefit("STOCK_RESTORED");
        assertIdempotentBenefit("COUPON_RESTORED");
        assertIdempotentBenefit("POINTS_RESTORED");
    }

    private RefundCommand refund(String id, String amount) { return new RefundCommand(1L, "test-tenant", id, new BigDecimal(amount)); }
    private BenefitRollbackCommand command(List<StockRollbackItem> items, Long couponHistoryId, Integer points) {
        return new BenefitRollbackCommand(1L, "test-tenant", "test-benefit-key", 100L, 10L, items, couponHistoryId, points);
    }

    private void assertIdempotentBenefit(String stepType) {
        AfterSaleJdbcRepository repository = mock(AfterSaleJdbcRepository.class);
        BenefitRollbackPort benefits = mock(BenefitRollbackPort.class);
        AtomicBenefitRollbackExecutionService service = new AtomicBenefitRollbackExecutionService(repository, benefits);
        TrustedAfterSaleContext context = new TrustedAfterSaleContext("test-tenant", 99L, "STAFF", "test-auth", "test-trace", "test-request");
        String key = "case-1-" + stepType;
        BenefitRollbackCommand facts = command(List.of(new StockRollbackItem(5L, 20L, 2)), 7L, 100);
        when(repository.findById("test-tenant", 1L)).thenReturn(java.util.Optional.of(
                new AfterSaleView(1L, 100L, "test-case-1", "test-rule", AfterSaleStatus.EXECUTING, new BigDecimal("10.01"))));
        when(repository.refundSucceeded(1L)).thenReturn(true);
        when(repository.claimSagaStep(1L, stepType, key)).thenReturn(true, false);
        when(repository.sagaStepStatus(1L, stepType, key)).thenReturn("SUCCEEDED");
        when(repository.loadTrustedBenefitCommand("test-tenant", 1L, key)).thenReturn(facts);
        if ("STOCK_RESTORED".equals(stepType)) when(benefits.restoreStock(facts)).thenReturn(new StepResult("SUCCEEDED", "ok"));
        if ("COUPON_RESTORED".equals(stepType)) when(benefits.restoreCoupon(facts)).thenReturn(new StepResult("SUCCEEDED", "ok"));
        if ("POINTS_RESTORED".equals(stepType)) when(benefits.restorePoints(facts)).thenReturn(new StepResult("SUCCEEDED", "ok"));

        assertEquals("SUCCEEDED", service.execute(context, 1L, stepType, key).status());
        assertEquals("SUCCEEDED", service.execute(context, 1L, stepType, key).status());
        if ("STOCK_RESTORED".equals(stepType)) verify(benefits).restoreStock(facts);
        if ("COUPON_RESTORED".equals(stepType)) verify(benefits).restoreCoupon(facts);
        if ("POINTS_RESTORED".equals(stepType)) verify(benefits).restorePoints(facts);
    }
}
