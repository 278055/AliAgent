package com.macro.mall.portal.aftersale.compensation;

import com.macro.mall.portal.aftersale.api.AfterSaleCommandPort;
import com.macro.mall.portal.aftersale.api.AfterSaleQueryPort;
import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.BenefitRollbackExecutionPort;
import com.macro.mall.portal.aftersale.api.NotificationCommand;
import com.macro.mall.portal.aftersale.api.NotificationPort;
import com.macro.mall.portal.aftersale.api.RefundCommand;
import com.macro.mall.portal.aftersale.api.RefundPort;
import com.macro.mall.portal.aftersale.api.RefundResult;
import com.macro.mall.portal.aftersale.api.SagaStepReport;
import com.macro.mall.portal.aftersale.api.StepResult;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 按冻结顺序推进退款、权益和通知，退款成功后绝不执行反向退款。 */
@Service
public final class CompensationExecutor {
    private static final List<String> BENEFIT_STEPS = List.of("STOCK_RESTORED", "COUPON_RESTORED", "POINTS_RESTORED");
    private final RefundPort refunds;
    private final BenefitRollbackExecutionPort benefits;
    private final NotificationPort notifications;
    private final AfterSaleCommandPort commands;
    private final AfterSaleQueryPort queries;

    public CompensationExecutor(RefundPort refunds, BenefitRollbackExecutionPort benefits, NotificationPort notifications,
                                AfterSaleCommandPort commands, AfterSaleQueryPort queries) {
        this.refunds = refunds;
        this.benefits = benefits;
        this.notifications = notifications;
        this.commands = commands;
        this.queries = queries;
    }

    public StepResult execute(TrustedAfterSaleContext context, Long caseId, String refundRequestId) {
        requireServiceContext(context);
        AfterSaleView afterSaleCase = queries.find(context, caseId)
                .orElseThrow(() -> new SecurityException("after-sale case not found"));
        RefundResult refund = refunds.refund(new RefundCommand(caseId, context.tenantId(), refundRequestId, afterSaleCase.requestedAmount()));
        if ("UNKNOWN".equals(refund.status())) refund = refunds.query(refundRequestId);
        if ("PROCESSING".equals(refund.status()) || "UNKNOWN".equals(refund.status())) {
            report(context, caseId, "REFUND_SUCCEEDED", refundRequestId, "PROCESSING", "REFUND_PROCESSING");
            return new StepResult("PROCESSING", "REFUND_PROCESSING");
        }
        if (!"SUCCEEDED".equals(refund.status())) {
            report(context, caseId, "REFUND_SUCCEEDED", refundRequestId, "FAILED", "REFUND_FAILED");
            return new StepResult("RETRY_PENDING", "REFUND_FAILED");
        }

        report(context, caseId, "REFUND_SUCCEEDED", refundRequestId, "SUCCEEDED", null);
        for (String step : BENEFIT_STEPS) {
            StepResult result = benefits.execute(context, caseId, step, "case-" + caseId + '-' + step);
            if (!"SUCCEEDED".equals(result.status())) {
                return new StepResult("MANUAL_RECONCILIATION", result.detail());
            }
        }

        String notificationId = "case-" + caseId + "-REFUND_RESULT";
        StepResult notification = notifications.notify(new NotificationCommand(caseId, context.tenantId(), notificationId, "REFUND_RESULT"));
        if (!"SUBMITTED".equals(notification.status())) {
            report(context, caseId, "NOTIFICATION_SENT", notificationId, "FAILED", "NOTIFICATION_RETRY_PENDING");
            return new StepResult("RETRY_PENDING", "NOTIFICATION_RETRY_PENDING");
        }
        report(context, caseId, "NOTIFICATION_SENT", notificationId, "SUCCEEDED", null);
        report(context, caseId, "COMPLETED", "case-" + caseId + "-COMPLETED", "SUCCEEDED", null);
        return new StepResult("SUCCEEDED", "compensation completed");
    }

    private void report(TrustedAfterSaleContext context, Long caseId, String stepType, String key, String status, String detail) {
        String source = context.tenantId() + ':' + caseId + ':' + stepType + ':' + key + ':' + status;
        String eventId = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
        commands.reportStep(context, caseId, new SagaStepReport(eventId, key, stepType, status, detail));
    }

    private void requireServiceContext(TrustedAfterSaleContext context) {
        if (context == null || !context.isStaff()) throw new SecurityException("service compensation requires staff context");
    }
}
