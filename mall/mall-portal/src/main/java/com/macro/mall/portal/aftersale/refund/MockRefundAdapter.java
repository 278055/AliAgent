package com.macro.mall.portal.aftersale.refund;

import com.macro.mall.portal.aftersale.api.RefundCommand;
import com.macro.mall.portal.aftersale.api.RefundPort;
import com.macro.mall.portal.aftersale.api.RefundResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** P6 专用 Mock 渠道，以退款请求号保存唯一退款事实。 */
@Component
public final class MockRefundAdapter implements RefundPort {
    private final Map<String, RefundFact> facts = new ConcurrentHashMap<>();

    @Override
    public RefundResult refund(RefundCommand command) {
        validate(command);
        return facts.computeIfAbsent(command.refundRequestId(), ignored -> create(command)).result();
    }

    @Override
    public RefundResult query(String refundRequestId) {
        if (refundRequestId == null || refundRequestId.isBlank()) throw new IllegalArgumentException("refundRequestId is required");
        RefundFact fact = facts.get(refundRequestId);
        if (fact == null) return new RefundResult(refundRequestId, "UNKNOWN", "MOCK_NOT_FOUND");
        fact.lastQueriedAt = Instant.now();
        if ("UNKNOWN".equals(fact.status)) {
            if (refundRequestId.contains("not-refunded")) {
                fact.status = "FAILED";
                fact.failureCategory = "MOCK_NOT_REFUNDED";
            } else {
                fact.status = refundRequestId.contains("timeout-success") ? "SUCCEEDED" : "PROCESSING";
            }
        }
        return fact.result();
    }

    public int factCount() { return facts.size(); }

    public RefundFactView fact(String refundRequestId) {
        RefundFact fact = facts.get(refundRequestId);
        if (fact == null) return null;
        return new RefundFactView(fact.caseId, fact.tenantId, fact.refundRequestId, fact.amount, fact.requestedAt,
                fact.status, fact.channelReference, fact.failureCategory, fact.lastQueriedAt);
    }

    private RefundFact create(RefundCommand command) {
        String requestId = command.refundRequestId();
        String status = requestId.contains("failed") ? "FAILED"
                : requestId.contains("timeout") ? "UNKNOWN"
                : requestId.contains("processing") ? "PROCESSING" : "SUCCEEDED";
        String failureCategory = "FAILED".equals(status) ? "MOCK_DECLINED" : "UNKNOWN".equals(status) ? "MOCK_TIMEOUT" : null;
        return new RefundFact(command.caseId(), command.tenantId(), requestId, command.amount(), Instant.now(), status,
                "mock-ref-" + requestId, failureCategory);
    }

    private void validate(RefundCommand command) {
        if (command == null || command.caseId() == null || command.tenantId() == null || command.tenantId().isBlank()
                || command.refundRequestId() == null || command.refundRequestId().isBlank()
                || command.amount() == null || command.amount().signum() < 0) {
            throw new IllegalArgumentException("invalid mock refund command");
        }
    }

    private static final class RefundFact {
        private final Long caseId;
        private final String tenantId;
        private final String refundRequestId;
        private final BigDecimal amount;
        private final Instant requestedAt;
        private String status;
        private final String channelReference;
        private String failureCategory;
        private Instant lastQueriedAt;

        private RefundFact(Long caseId, String tenantId, String refundRequestId, BigDecimal amount, Instant requestedAt,
                           String status, String channelReference, String failureCategory) {
            this.caseId = caseId;
            this.tenantId = tenantId;
            this.refundRequestId = refundRequestId;
            this.amount = amount;
            this.requestedAt = requestedAt;
            this.status = status;
            this.channelReference = channelReference;
            this.failureCategory = failureCategory;
        }

        private RefundResult result() {
            String detail = "FAILED".equals(status) || "UNKNOWN".equals(status) ? failureCategory : channelReference;
            return new RefundResult(refundRequestId, status, detail);
        }
    }

    public static final class RefundFactView {
        private final Long caseId;
        private final String tenantId;
        private final String refundRequestId;
        private final BigDecimal amount;
        private final Instant requestedAt;
        private final String status;
        private final String channelReference;
        private final String failureCategory;
        private final Instant lastQueriedAt;

        private RefundFactView(Long caseId, String tenantId, String refundRequestId, BigDecimal amount,
                               Instant requestedAt, String status, String channelReference,
                               String failureCategory, Instant lastQueriedAt) {
            this.caseId = caseId;
            this.tenantId = tenantId;
            this.refundRequestId = refundRequestId;
            this.amount = amount;
            this.requestedAt = requestedAt;
            this.status = status;
            this.channelReference = channelReference;
            this.failureCategory = failureCategory;
            this.lastQueriedAt = lastQueriedAt;
        }

        public Long caseId() { return caseId; }
        public String tenantId() { return tenantId; }
        public String refundRequestId() { return refundRequestId; }
        public BigDecimal amount() { return amount; }
        public Instant requestedAt() { return requestedAt; }
        public String status() { return status; }
        public String channelReference() { return channelReference; }
        public String failureCategory() { return failureCategory; }
        public Instant lastQueriedAt() { return lastQueriedAt; }
    }
}
