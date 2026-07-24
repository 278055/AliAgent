package com.macro.mall.portal.aftersale;

import com.macro.mall.portal.aftersale.core.AfterSaleService;
import com.macro.mall.portal.aftersale.core.AfterSaleStatus;
import com.macro.mall.portal.aftersale.core.AfterSaleTestFixture;
import com.macro.mall.portal.aftersale.core.AfterSaleCase;
import com.macro.mall.portal.aftersale.core.AfterSaleCommand;
import com.macro.mall.portal.aftersale.core.PortalOrderCancellationAdapter;
import com.macro.mall.portal.aftersale.core.ResolvedOrderTenant;
import com.macro.mall.portal.aftersale.persistence.JdbcOrderTenantResolver;
import com.macro.mall.portal.aftersale.api.BenefitRollbackPort;
import com.macro.mall.portal.aftersale.api.NotificationPort;
import com.macro.mall.portal.aftersale.api.RefundPort;
import com.macro.mall.portal.aftersale.api.SagaStepReport;
import com.macro.mall.portal.aftersale.api.ManualReconciliationCommand;
import com.macro.mall.portal.service.OmsPortalOrderService;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AfterSaleServiceTest {

    @Test
    void 创建草稿时固定规则版本并写入确认事件() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase result = service.createDraft(AfterSaleTestFixture.command("test-command-1", "test-key-1", 1L, 10L, 100L, new BigDecimal("99.00"), false));
        assertEquals("test-rules-v1", result.ruleVersionId());
        assertEquals(AfterSaleStatus.WAITING_USER_CONFIRMATION, result.status());
        assertEquals(1, service.outboxSize());
    }

    @Test
    void 确认前不得执行订单取消写操作() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase draft = service.createDraft(AfterSaleTestFixture.command("test-command-2", "test-key-2", 1L, 10L, 101L, new BigDecimal("99.00"), false));
        assertThrows(IllegalStateException.class, () -> service.execute(draft.caseId()));
        assertEquals(0, service.cancelledOrderCount());
    }

    @Test
    void 相同命令和幂等键返回原结果且不重复执行() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCommand command = AfterSaleTestFixture.command("test-command-3", "test-key-3", 1L, 10L, 102L, new BigDecimal("99.00"), false);
        AfterSaleCase first = service.createDraft(command);
        AfterSaleCase byCommand = service.createDraft(command);
        AfterSaleCase byKey = service.createDraft(AfterSaleTestFixture.command("test-command-4", "test-key-3", 1L, 10L, 102L, new BigDecimal("99.00"), false));
        assertEquals(first.caseId(), byCommand.caseId());
        assertEquals(first.caseId(), byKey.caseId());
        assertEquals(1, service.caseCount());
    }

    @Test
    void 活动申请冲突和非法状态转换被拒绝并审计() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase draft = service.createDraft(AfterSaleTestFixture.command("test-command-5", "test-key-5", 1L, 10L, 103L, new BigDecimal("99.00"), false));
        assertThrows(IllegalStateException.class, () -> service.createDraft(AfterSaleTestFixture.command("test-command-6", "test-key-6", 1L, 10L, 103L, new BigDecimal("99.00"), false)));
        assertThrows(IllegalStateException.class, () -> service.transition(draft.caseId(), AfterSaleStatus.COMPLETED));
        assertEquals(2, service.auditFailureCount());
    }

    @Test
    void 未支付确认后自动取消而已支付订单走审批路由() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase unpaid = service.createDraft(AfterSaleTestFixture.command("test-command-7", "test-key-7", 1L, 10L, 104L, new BigDecimal("99.00"), false));
        assertEquals(AfterSaleStatus.EXECUTING, service.confirm(unpaid.caseId()).status());
        assertEquals(1, service.cancelledOrderCount());
        AfterSaleCase normal = service.createDraft(AfterSaleTestFixture.command("test-command-8", "test-key-8", 1L, 10L, 105L, new BigDecimal("499.99"), false, true));
        AfterSaleCase large = service.createDraft(AfterSaleTestFixture.command("test-command-9", "test-key-9", 1L, 10L, 106L, new BigDecimal("500.00"), false, true));
        AfterSaleCase risky = service.createDraft(AfterSaleTestFixture.command("test-command-10", "test-key-10", 1L, 10L, 107L, new BigDecimal("1.00"), true, true));
        assertEquals(AfterSaleStatus.WAITING_STAFF_APPROVAL, service.confirm(normal.caseId()).status());
        assertEquals(AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL, service.confirm(large.caseId()).status());
        assertEquals(AfterSaleStatus.WAITING_SUPERVISOR_APPROVAL, service.confirm(risky.caseId()).status());
    }

    @Test
    void 订单归属租户已支付校验以及重试规则版本固定() {
        AfterSaleService service = AfterSaleTestFixture.service();
        assertThrows(SecurityException.class, () -> service.createDraft(AfterSaleTestFixture.command("test-command-11", "test-key-11", 2L, 10L, 108L, new BigDecimal("1.00"), false)));
        assertThrows(SecurityException.class, () -> service.createDraft(AfterSaleTestFixture.command("test-command-12", "test-key-12", 1L, 99L, 109L, new BigDecimal("1.00"), false)));
        AfterSaleCase paid = service.createDraft(AfterSaleTestFixture.command("test-command-13", "test-key-13", 1L, 10L, 110L, new BigDecimal("1.00"), false, true));
        service.confirm(paid.caseId());
        assertThrows(IllegalStateException.class, () -> service.execute(paid.caseId()));
        service.approve(paid.caseId(), false);
        service.transition(paid.caseId(), AfterSaleStatus.RETRY_PENDING);
        String version = paid.ruleVersionId();
        service.retry(paid.caseId());
        assertEquals(version, service.find(paid.caseId()).ruleVersionId());
    }

    @Test
    void outbox业务写入原子且inbox重复事件不重复推进Saga() {
        AfterSaleService service = AfterSaleTestFixture.service();
        service.failNextTransaction();
        assertThrows(IllegalStateException.class, () -> service.createDraft(AfterSaleTestFixture.command("test-command-14", "test-key-14", 1L, 10L, 111L, new BigDecimal("1.00"), false)));
        assertEquals(0, service.caseCount());
        assertEquals(0, service.outboxSize());
        AfterSaleCase caseResult = service.createDraft(AfterSaleTestFixture.command("test-command-15", "test-key-15", 1L, 10L, 112L, new BigDecimal("1.00"), false));
        service.confirm(caseResult.caseId());
        service.handleRefundSucceeded("test-event-1", caseResult.caseId());
        service.handleRefundSucceeded("test-event-1", caseResult.caseId());
        assertEquals(1, service.sagaAdvanceCount());
    }

    @Test
    void 退款成功后权益失败进入人工对账() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase result = service.createDraft(AfterSaleTestFixture.command("test-command-16", "test-key-16", 1L, 10L, 113L, new BigDecimal("1.00"), false, true));
        service.confirm(result.caseId());
        service.approve(result.caseId(), false);
        service.handleRefundSucceeded("test-event-2", result.caseId());
        service.handleBenefitRollbackFailed("test-event-3", result.caseId());
        assertEquals(AfterSaleStatus.MANUAL_RECONCILIATION, service.find(result.caseId()).status());
    }

    @Test
    void 可重试失败进入重试等待且重复事件不重复推进() {
        AfterSaleService service = AfterSaleTestFixture.service();
        AfterSaleCase result = service.createDraft(AfterSaleTestFixture.command("test-command-17", "test-key-17", 1L, 10L, 114L, new BigDecimal("1.00"), false, true));
        service.confirm(result.caseId());
        service.approve(result.caseId(), false);
        assertEquals(AfterSaleStatus.RETRY_PENDING, service.handleRetryableFailure("test-event-4", result.caseId()).status());
        assertEquals(AfterSaleStatus.RETRY_PENDING, service.handleRetryableFailure("test-event-4", result.caseId()).status());
    }

    @Test
    void 订单取消适配器在前后校验通过时复用既有服务() {
        OmsOrderMapper mapper = mock(OmsOrderMapper.class);
        OmsPortalOrderService portalOrderService = mock(OmsPortalOrderService.class);
        OmsOrder order = new OmsOrder();
        order.setId(114L); order.setMemberId(10L); order.setStatus(0); order.setDeleteStatus(0);
        OmsOrder closed = new OmsOrder();
        closed.setId(114L); closed.setMemberId(10L); closed.setStatus(4); closed.setDeleteStatus(0);
        when(mapper.selectByPrimaryKey(114L)).thenReturn(order, closed);
        PortalOrderCancellationAdapter adapter = new PortalOrderCancellationAdapter(mapper, portalOrderService, orderId -> new ResolvedOrderTenant(orderId, "test-tenant", "HISTORICAL_BACKFILL", Instant.now()));
        AfterSaleCase caseValue = new AfterSaleCase("test-case-adapter", "test-tenant", 10L, 114L, new BigDecimal("1.00"), false, false, "test-rules-v1", AfterSaleStatus.EXECUTING);
        adapter.cancel(caseValue);
        verify(portalOrderService).cancelOrder(114L);
    }

    @Test
    void 订单租户解析器只能从绑定表读取且未绑定或多绑定时拒绝() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcOrderTenantResolver resolver = new JdbcOrderTenantResolver(jdbc);
        when(jdbc.query(eq("SELECT order_id, tenant_id, source, bound_at FROM order_tenant_binding WHERE order_id=?"), any(RowMapper.class), eq(115L)))
                .thenReturn(Arrays.asList(new ResolvedOrderTenant(115L, "test-tenant", "HISTORICAL_BACKFILL", Instant.now())));
        assertEquals("test-tenant", resolver.resolve(115L).tenantId());
        when(jdbc.query(eq("SELECT order_id, tenant_id, source, bound_at FROM order_tenant_binding WHERE order_id=?"), any(RowMapper.class), eq(116L)))
                .thenReturn(Collections.emptyList());
        assertThrows(SecurityException.class, () -> resolver.resolve(116L));
        when(jdbc.query(eq("SELECT order_id, tenant_id, source, bound_at FROM order_tenant_binding WHERE order_id=?"), any(RowMapper.class), eq(117L)))
                .thenReturn(Arrays.asList(new ResolvedOrderTenant(117L, "test-tenant", "ORDER_CREATION", Instant.now()), new ResolvedOrderTenant(117L, "other", "ORDER_CREATION", Instant.now())));
        assertThrows(SecurityException.class, () -> resolver.resolve(117L));
    }

    @Test
    void P6C所需端口与Saga上报命令保持独立且携带幂等键() {
        assertEquals(2, RefundPort.class.getDeclaredMethods().length);
        assertEquals(3, BenefitRollbackPort.class.getDeclaredMethods().length);
        assertEquals(1, NotificationPort.class.getDeclaredMethods().length);
        SagaStepReport report = new SagaStepReport("test-event-5", "test-step-key", "REFUND_SUCCEEDED", "SUCCEEDED", null);
        ManualReconciliationCommand command = new ManualReconciliationCommand("test-command-18", "test-key-18", "COMPLETED");
        assertEquals("test-step-key", report.idempotencyKey());
        assertEquals("test-command-18", command.commandId());
    }
}
