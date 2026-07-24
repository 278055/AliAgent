package com.macro.mall.portal.aftersale.compensation;

import com.macro.mall.portal.aftersale.api.AfterSaleCommandPort;
import com.macro.mall.portal.aftersale.api.AfterSaleView;
import com.macro.mall.portal.aftersale.api.ManualReconciliationCommand;
import com.macro.mall.portal.aftersale.api.RefundPort;
import com.macro.mall.portal.aftersale.api.RefundResult;
import com.macro.mall.portal.aftersale.api.TrustedAfterSaleContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 汇总当前事实并阻止未解决差异被错误标记为完成。 */
@Service
public final class ManualReconciliationService {
    private static final String STEP_QUERY = "SELECT COUNT(*) FROM after_sale_saga_step s JOIN after_sale_saga g ON s.saga_id=g.id WHERE g.case_id=? AND g.tenant_id=? AND s.step_type=? AND s.status='SUCCEEDED'";
    private final JdbcTemplate jdbc;
    private final RefundPort refunds;
    private final AfterSaleCommandPort commands;

    public ManualReconciliationService(JdbcTemplate jdbc, RefundPort refunds, AfterSaleCommandPort commands) {
        this.jdbc = jdbc;
        this.refunds = refunds;
        this.commands = commands;
    }

    public ReconciliationSnapshot inspect(TrustedAfterSaleContext context, Long caseId, String refundRequestId) {
        requireStaff(context);
        RefundResult refund = refunds.query(refundRequestId);
        String stock = stepStatus(caseId, context.tenantId(), "STOCK_RESTORED");
        String coupon = stepStatus(caseId, context.tenantId(), "COUPON_RESTORED");
        String points = stepStatus(caseId, context.tenantId(), "POINTS_RESTORED");
        boolean resolved = "SUCCEEDED".equals(refund.status()) && "SUCCEEDED".equals(stock)
                && "SUCCEEDED".equals(coupon) && "SUCCEEDED".equals(points);
        return new ReconciliationSnapshot(refund.status(), stock, coupon, points, resolved);
    }

    public AfterSaleView resolve(TrustedAfterSaleContext context, Long caseId, String refundRequestId,
                                 ManualReconciliationCommand command) {
        ReconciliationSnapshot snapshot = inspect(context, caseId, refundRequestId);
        if ("COMPLETED".equals(command.resolution()) && !snapshot.resolved()) {
            throw new IllegalStateException("manual reconciliation differences remain unresolved");
        }
        return commands.resolveManualReconciliation(context, caseId, command);
    }

    private String stepStatus(Long caseId, String tenantId, String stepType) {
        Integer count = jdbc.queryForObject(STEP_QUERY, Integer.class, caseId, tenantId, stepType);
        return count != null && count > 0 ? "SUCCEEDED" : "DIFFERENCE";
    }

    private void requireStaff(TrustedAfterSaleContext context) {
        if (context == null || !context.isStaff()) throw new SecurityException("manual reconciliation requires staff context");
    }

    public static final class ReconciliationSnapshot {
        private final String refundStatus;
        private final String stockStatus;
        private final String couponStatus;
        private final String pointsStatus;
        private final boolean resolved;

        private ReconciliationSnapshot(String refundStatus, String stockStatus, String couponStatus,
                                       String pointsStatus, boolean resolved) {
            this.refundStatus = refundStatus;
            this.stockStatus = stockStatus;
            this.couponStatus = couponStatus;
            this.pointsStatus = pointsStatus;
            this.resolved = resolved;
        }

        public String refundStatus() { return refundStatus; }
        public String stockStatus() { return stockStatus; }
        public String couponStatus() { return couponStatus; }
        public String pointsStatus() { return pointsStatus; }
        public boolean resolved() { return resolved; }
    }
}
