package com.macro.mall.portal.aftersale;

import com.macro.mall.portal.aftersale.api.AfterSaleCommandPort;
import com.macro.mall.portal.aftersale.api.AfterSaleQueryPort;
import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.BenefitRollbackExecutionPort;
import com.macro.mall.portal.aftersale.api.NotificationCommand;
import com.macro.mall.portal.aftersale.api.NotificationPort;
import com.macro.mall.portal.aftersale.api.ManualReconciliationCommand;
import com.macro.mall.portal.aftersale.api.RefundCommand;
import com.macro.mall.portal.aftersale.api.RefundPort;
import com.macro.mall.portal.aftersale.api.RefundResult;
import com.macro.mall.portal.aftersale.api.SagaStepReport;
import com.macro.mall.portal.aftersale.api.StepResult;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import com.macro.mall.portal.aftersale.compensation.CompensationExecutor;
import com.macro.mall.portal.aftersale.compensation.ManualReconciliationService;
import com.macro.mall.portal.aftersale.core.AfterSaleStatus;
import com.macro.mall.portal.aftersale.core.OrderCancellationPort;
import com.macro.mall.portal.aftersale.core.OrderTenantResolver;
import com.macro.mall.portal.aftersale.core.PersistentAfterSaleService;
import com.macro.mall.portal.aftersale.core.RuleVersionResolver;
import com.macro.mall.portal.aftersale.notification.MockNotificationAdapter;
import com.macro.mall.portal.aftersale.persistence.AfterSaleJdbcRepository;
import com.macro.mall.mapper.OmsOrderMapper;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P6CNotificationCompensationTest {
    @Test
    void 通知只表示已提交且相同通知号不重复提交() {
        MockNotificationAdapter adapter = new MockNotificationAdapter();
        NotificationCommand command = new NotificationCommand(1L, "test-tenant", "test-notification-1", "REFUND_RESULT");

        assertEquals("SUBMITTED", adapter.notify(command).status());
        assertEquals("SUBMITTED", adapter.notify(command).status());
        assertEquals(1, adapter.submissionCount());
        assertFalse(adapter.notify(command).detail().contains("DELIVERED"));
    }

    @Test
    void 通知失败进入重试且支持冻结的五类通知() {
        MockNotificationAdapter adapter = new MockNotificationAdapter();
        assertEquals("RETRY_PENDING", adapter.notify(notification("test-notification-fail", "REFUND_RESULT")).status());
        assertEquals("SUBMITTED", adapter.notify(notification("test-notification-submit", "AFTERSALE_SUBMITTED")).status());
        assertEquals("SUBMITTED", adapter.notify(notification("test-notification-approval", "APPROVAL_RESULT")).status());
        assertEquals("SUBMITTED", adapter.notify(notification("test-notification-benefit", "BENEFIT_FAILURE")).status());
        assertEquals("SUBMITTED", adapter.notify(notification("test-notification-manual", "MANUAL_RECONCILIATION")).status());
    }

    @Test
    void 通知失败后相同通知号可重试且成功后不重复提交() {
        MockNotificationAdapter adapter = new MockNotificationAdapter();
        NotificationCommand command = notification("test-notification-fail-once", "REFUND_RESULT");

        assertEquals("RETRY_PENDING", adapter.notify(command).status());
        assertEquals("SUBMITTED", adapter.notify(command).status());
        assertEquals("SUBMITTED", adapter.notify(command).status());
        assertEquals(2, adapter.submissionCount());
    }

    @Test
    void 并发重复通知号只提交一次() throws Exception {
        MockNotificationAdapter adapter = new MockNotificationAdapter();
        NotificationCommand command = notification("test-notification-concurrent", "REFUND_RESULT");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int i = 0; i < 8; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    adapter.notify(command);
                    return null;
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(1, adapter.submissionCount());
    }

    @Test
    void 相同通知号在不同租户之间互不冲突() {
        MockNotificationAdapter adapter = new MockNotificationAdapter();

        assertEquals("SUBMITTED", adapter.notify(new NotificationCommand(1L, "test-tenant-a", "test-notification-shared", "REFUND_RESULT")).status());
        assertEquals("SUBMITTED", adapter.notify(new NotificationCommand(2L, "test-tenant-b", "test-notification-shared", "REFUND_RESULT")).status());
        assertEquals(2, adapter.submissionCount());
    }

    @Test
    void 未知退款查询处理中时不执行权益() {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class))).thenReturn(new RefundResult("test-refund-timeout", "UNKNOWN", "MOCK_TIMEOUT"));
        when(fixture.refunds.query("test-refund-timeout")).thenReturn(new RefundResult("test-refund-timeout", "PROCESSING", "MOCK_PROCESSING"));

        assertEquals("PROCESSING", fixture.executor.execute(context(), 1L, "test-refund-timeout").status());
        verify(fixture.refunds).query("test-refund-timeout");
        verify(fixture.benefits, never()).execute(any(), any(), any(), any());
    }

    @Test
    void 未知退款查询确认未退款后由Saga决定重试且不执行权益() {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class))).thenReturn(new RefundResult("test-refund-timeout-not-refunded", "UNKNOWN", "MOCK_TIMEOUT"));
        when(fixture.refunds.query("test-refund-timeout-not-refunded"))
                .thenReturn(new RefundResult("test-refund-timeout-not-refunded", "FAILED", "MOCK_NOT_REFUNDED"));

        assertEquals("RETRY_PENDING", fixture.executor.execute(context(), 1L, "test-refund-timeout-not-refunded").status());
        verify(fixture.commands).reportStep(any(), eq(1L), step("REFUND_SUCCEEDED", "FAILED"));
        verify(fixture.benefits, never()).execute(any(), any(), any(), any());
    }

    @Test
    void 未知退款查询成功后按冻结顺序执行权益和通知() {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class))).thenReturn(new RefundResult("test-refund-timeout-success", "UNKNOWN", "MOCK_TIMEOUT"));
        when(fixture.refunds.query("test-refund-timeout-success")).thenReturn(new RefundResult("test-refund-timeout-success", "SUCCEEDED", "mock-ref"));
        when(fixture.benefits.execute(any(), eq(1L), any(), any())).thenReturn(new StepResult("SUCCEEDED", "ok"));
        when(fixture.notifications.notify(any())).thenReturn(new StepResult("SUBMITTED", "accepted"));

        assertEquals("SUCCEEDED", fixture.executor.execute(context(), 1L, "test-refund-timeout-success").status());

        InOrder order = inOrder(fixture.refunds, fixture.commands, fixture.benefits, fixture.notifications);
        order.verify(fixture.refunds).refund(any(RefundCommand.class));
        order.verify(fixture.refunds).query("test-refund-timeout-success");
        order.verify(fixture.commands).reportStep(any(), eq(1L), step("REFUND_SUCCEEDED", "SUCCEEDED"));
        order.verify(fixture.benefits).execute(any(), eq(1L), eq("STOCK_RESTORED"), eq("case-1-STOCK_RESTORED"));
        order.verify(fixture.benefits).execute(any(), eq(1L), eq("COUPON_RESTORED"), eq("case-1-COUPON_RESTORED"));
        order.verify(fixture.benefits).execute(any(), eq(1L), eq("POINTS_RESTORED"), eq("case-1-POINTS_RESTORED"));
        order.verify(fixture.notifications).notify(any(NotificationCommand.class));
        order.verify(fixture.commands).reportStep(any(), eq(1L), step("NOTIFICATION_SENT", "SUCCEEDED"));
        order.verify(fixture.commands).reportStep(any(), eq(1L), step("COMPLETED", "SUCCEEDED"));
    }

    @Test
    void 退款成功后任一权益失败立即停止且不反向退款或覆盖人工对账事实() {
        assertBenefitFailure("STOCK_RESTORED", "COUPON_RESTORED", "POINTS_RESTORED");
        assertBenefitFailure("COUPON_RESTORED", "POINTS_RESTORED", null);
        assertBenefitFailure("POINTS_RESTORED", null, null);
    }

    @Test
    void 通知失败只将通知步骤置为可重试不回滚已完成事实() {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class))).thenReturn(new RefundResult("test-refund-success", "SUCCEEDED", "mock-ref"));
        when(fixture.benefits.execute(any(), eq(1L), any(), any())).thenReturn(new StepResult("SUCCEEDED", "ok"));
        when(fixture.notifications.notify(any())).thenReturn(new StepResult("RETRY_PENDING", "MOCK_NOTIFICATION_REJECTED"));

        assertEquals("RETRY_PENDING", fixture.executor.execute(context(), 1L, "test-refund-success").status());
        verify(fixture.commands).reportStep(any(), eq(1L), step("NOTIFICATION_SENT", "FAILED"));
        verify(fixture.refunds, times(1)).refund(any());
    }

    @Test
    void 步骤上报只记录稳定分类不传播敏感渠道详情() {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class)))
                .thenReturn(new RefundResult("test-refund-sensitive", "FAILED", "JWT payment-token full-address"));

        fixture.executor.execute(context(), 1L, "test-refund-sensitive");

        ArgumentCaptor<SagaStepReport> reports = ArgumentCaptor.forClass(SagaStepReport.class);
        verify(fixture.commands).reportStep(any(), eq(1L), reports.capture());
        assertEquals("REFUND_FAILED", reports.getValue().errorMessage());
        assertFalse(reports.getValue().errorMessage().contains("token"));
        assertFalse(reports.getValue().errorMessage().contains("address"));
    }

    @Test
    void 人工对账查询四类事实且有差异时拒绝完成() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RefundPort refunds = mock(RefundPort.class);
        AfterSaleCommandPort commands = mock(AfterSaleCommandPort.class);
        when(refunds.query("test-refund-manual")).thenReturn(new RefundResult("test-refund-manual", "SUCCEEDED", "mock-ref"));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(1L), eq("test-tenant"), eq("STOCK_RESTORED"))).thenReturn(1);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(1L), eq("test-tenant"), eq("COUPON_RESTORED"))).thenReturn(0);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(1L), eq("test-tenant"), eq("POINTS_RESTORED"))).thenReturn(1);
        ManualReconciliationService service = new ManualReconciliationService(jdbc, refunds, commands);

        ManualReconciliationService.ReconciliationSnapshot snapshot = service.inspect(context(), 1L, "test-refund-manual");

        assertEquals("SUCCEEDED", snapshot.refundStatus());
        assertEquals("SUCCEEDED", snapshot.stockStatus());
        assertEquals("DIFFERENCE", snapshot.couponStatus());
        assertEquals("SUCCEEDED", snapshot.pointsStatus());
        assertFalse(snapshot.resolved());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.resolve(context(), 1L, "test-refund-manual",
                        new ManualReconciliationCommand("test-command-manual", "test-key-manual", "COMPLETED")));
        verify(commands, never()).resolveManualReconciliation(any(), any(), any());
    }

    @Test
    void 人工对账无差异时交由P6A幂等推进最终状态() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RefundPort refunds = mock(RefundPort.class);
        AfterSaleCommandPort commands = mock(AfterSaleCommandPort.class);
        when(refunds.query("test-refund-manual-ok")).thenReturn(new RefundResult("test-refund-manual-ok", "SUCCEEDED", "mock-ref"));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(1L), eq("test-tenant"), any(String.class))).thenReturn(1);
        AfterSaleView completed = new AfterSaleView(1L, 100L, "test-case-1", "test-rule", AfterSaleStatus.COMPLETED, new BigDecimal("10.01"));
        when(commands.resolveManualReconciliation(any(), eq(1L), any())).thenReturn(completed);
        ManualReconciliationService service = new ManualReconciliationService(jdbc, refunds, commands);
        ManualReconciliationCommand command = new ManualReconciliationCommand("test-command-manual-ok", "test-key-manual-ok", "COMPLETED");
        TrustedAfterSaleContext context = context();

        assertEquals(AfterSaleStatus.COMPLETED, service.resolve(context, 1L, "test-refund-manual-ok", command).status());
        assertEquals(AfterSaleStatus.COMPLETED, service.resolve(context, 1L, "test-refund-manual-ok", command).status());
        verify(commands, times(2)).resolveManualReconciliation(context, 1L, command);
    }

    @Test
    void P6A人工对账重复命令只推进一次() {
        AfterSaleJdbcRepository repository = mock(AfterSaleJdbcRepository.class);
        PersistentAfterSaleService service = new PersistentAfterSaleService(repository, mock(RuleVersionResolver.class),
                mock(OrderTenantResolver.class), mock(OmsOrderMapper.class), mock(OrderCancellationPort.class));
        TrustedAfterSaleContext context = context();
        ManualReconciliationCommand command = new ManualReconciliationCommand("test-command-manual-idempotent", "test-key-manual-idempotent", "COMPLETED");
        AfterSaleView pending = new AfterSaleView(1L, 100L, "test-case-1", "test-rule", AfterSaleStatus.MANUAL_RECONCILIATION, new BigDecimal("10.01"));
        AfterSaleView completed = new AfterSaleView(1L, 100L, "test-case-1", "test-rule", AfterSaleStatus.COMPLETED, new BigDecimal("10.01"));
        when(repository.findById("test-tenant", 1L)).thenReturn(Optional.of(pending), Optional.of(completed), Optional.of(completed));
        when(repository.claimInbox("test-command-manual-idempotent", "manual-reconciliation", "test-tenant")).thenReturn(true, false);

        assertEquals(AfterSaleStatus.COMPLETED, service.resolveManualReconciliation(context, 1L, command).status());
        assertEquals(AfterSaleStatus.COMPLETED, service.resolveManualReconciliation(context, 1L, command).status());
        verify(repository, times(1)).updateStatus(1L, AfterSaleStatus.COMPLETED);
    }

    private NotificationCommand notification(String id, String eventType) {
        return new NotificationCommand(1L, "test-tenant", id, eventType);
    }

    private void assertBenefitFailure(String failedStep, String firstSkipped, String secondSkipped) {
        Fixture fixture = fixture();
        when(fixture.refunds.refund(any(RefundCommand.class))).thenReturn(new RefundResult("test-refund-success", "SUCCEEDED", "mock-ref"));
        when(fixture.benefits.execute(any(), eq(1L), any(), any())).thenAnswer(invocation -> {
            String step = invocation.getArgument(2);
            return failedStep.equals(step) ? new StepResult("FAILED", step + "_FAILED") : new StepResult("SUCCEEDED", "ok");
        });

        assertEquals("MANUAL_RECONCILIATION", fixture.executor.execute(context(), 1L, "test-refund-success").status());
        if (firstSkipped != null) verify(fixture.benefits, never()).execute(any(), eq(1L), eq(firstSkipped), any());
        if (secondSkipped != null) verify(fixture.benefits, never()).execute(any(), eq(1L), eq(secondSkipped), any());
        verify(fixture.notifications, never()).notify(any());
        verify(fixture.refunds, times(1)).refund(any());
        ArgumentCaptor<SagaStepReport> reports = ArgumentCaptor.forClass(SagaStepReport.class);
        verify(fixture.commands).reportStep(any(), eq(1L), reports.capture());
        assertEquals("REFUND_SUCCEEDED", reports.getValue().stepType());
        assertTrue(reports.getValue().status().equals("SUCCEEDED"));
    }

    private Fixture fixture() {
        RefundPort refunds = mock(RefundPort.class);
        BenefitRollbackExecutionPort benefits = mock(BenefitRollbackExecutionPort.class);
        NotificationPort notifications = mock(NotificationPort.class);
        AfterSaleCommandPort commands = mock(AfterSaleCommandPort.class);
        AfterSaleQueryPort queries = mock(AfterSaleQueryPort.class);
        when(queries.find(any(), eq(1L))).thenReturn(Optional.of(new AfterSaleView(1L, 100L, "test-case-1", "test-rule", AfterSaleStatus.EXECUTING, new BigDecimal("10.01"))));
        return new Fixture(refunds, benefits, notifications, commands,
                new CompensationExecutor(refunds, benefits, notifications, commands, queries));
    }

    private TrustedAfterSaleContext context() {
        return new TrustedAfterSaleContext("test-tenant", 99L, "STAFF", "test-auth", "test-trace", "test-request");
    }

    private SagaStepReport step(String stepType, String status) {
        return org.mockito.ArgumentMatchers.argThat(report -> stepType.equals(report.stepType()) && status.equals(report.status()));
    }

    private static final class Fixture {
        private final RefundPort refunds;
        private final BenefitRollbackExecutionPort benefits;
        private final NotificationPort notifications;
        private final AfterSaleCommandPort commands;
        private final CompensationExecutor executor;

        private Fixture(RefundPort refunds, BenefitRollbackExecutionPort benefits, NotificationPort notifications,
                        AfterSaleCommandPort commands, CompensationExecutor executor) {
            this.refunds = refunds;
            this.benefits = benefits;
            this.notifications = notifications;
            this.commands = commands;
            this.executor = executor;
        }
    }
}
