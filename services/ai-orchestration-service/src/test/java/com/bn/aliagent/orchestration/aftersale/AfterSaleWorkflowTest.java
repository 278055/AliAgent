package com.bn.aliagent.orchestration.aftersale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.orchestration.confirmation.ConfirmationCard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AfterSaleWorkflowTest {
    private final UUID requestId = UUID.randomUUID();
    private final UUID snapshotId = UUID.randomUUID();
    private final TrustedAfterSaleContext context = new TrustedAfterSaleContext(
            "test-p6-b-tenant", "test-member", "MEMBER", "trace-test", requestId, snapshotId, "service-jwt");

    @Test
    void identifiesAllAfterSaleIntentsWithoutChangingReadOnlyIntentHandling() {
        assertEquals(AfterSaleIntent.CANCEL_UNPAID, AfterSaleIntent.classify("取消未付款订单"));
        assertEquals(AfterSaleIntent.REFUND_PAID, AfterSaleIntent.classify("已付款退款"));
        assertEquals(AfterSaleIntent.RETURN_REFUND, AfterSaleIntent.classify("我要退货退款"));
        assertEquals(AfterSaleIntent.QUERY_STATUS, AfterSaleIntent.classify("查询售后进度"));
        assertEquals(AfterSaleIntent.SUPPLEMENT_MATERIAL, AfterSaleIntent.classify("补充售后材料"));
        assertEquals(AfterSaleIntent.HUMAN_HANDOFF, AfterSaleIntent.classify("转人工客服"));
        assertEquals(AfterSaleIntent.CLARIFY, AfterSaleIntent.classify("帮我处理一下"));
        assertFalse(AfterSaleIntent.isAfterSale("查询订单物流"));
    }

    @Test
    void collectsOnlyMissingRequiredMaterialsAcrossTurns() {
        AfterSaleWorkflow workflow = workflow(new StubMall());
        AfterSaleResult first = workflow.collect(context, AfterSaleIntent.REFUND_PAID, Map.of("orderId", "101"));
        assertEquals(List.of("orderItemId", "reason"), first.missingFields());
        AfterSaleResult second = workflow.collect(context, AfterSaleIntent.REFUND_PAID,
                Map.of("orderItemId", "2", "reason", "不想要了", "quantity", "1"));
        assertEquals(List.of(), second.missingFields());
    }

    @Test
    void createsConfirmationCardFromMallFactsAndDoesNotWriteBeforeConfirmation() {
        StubMall mall = new StubMall();
        AfterSaleWorkflow workflow = workflow(mall);
        AfterSaleResult result = workflow.collect(context, AfterSaleIntent.REFUND_PAID,
                Map.of("orderId", "101", "orderItemId", "2", "reason", "质量问题", "quantity", "1"));

        ConfirmationCard card = result.confirmationCard();
        assertEquals("99.00", card.amountFact().toPlainString());
        assertEquals("101", card.orderId());
        assertEquals(AfterSaleStatus.WAITING_USER_CONFIRMATION, result.status());
        assertEquals(0, mall.submitCalls);
    }

    @Test
    void requiresSupervisorPromptForFiveHundredOrMore() {
        StubMall mall = new StubMall();
        mall.fact = mall.fact.withAmount(new BigDecimal("500.00"));
        AfterSaleResult result = workflow(mall).collect(context, AfterSaleIntent.REFUND_PAID,
                Map.of("orderId", "101", "orderItemId", "2", "reason", "质量问题"));
        assertEquals(true, result.confirmationCard().supervisorApprovalRequired());
    }

    @Test
    void explicitConfirmationSubmitsOneControlledCommandAndReplayUsesSameKey() {
        StubMall mall = new StubMall();
        AfterSaleWorkflow workflow = workflow(mall);
        ConfirmationCard card = workflow.collect(context, AfterSaleIntent.CANCEL_UNPAID,
                Map.of("orderId", "101", "reason", "误下单")).confirmationCard();

        workflow.confirm(context, card.actionId(), true);
        workflow.confirm(context, card.actionId(), true);

        assertEquals(1, mall.submitCalls);
        assertEquals(requestId, mall.lastCommand.requestId());
        assertEquals(snapshotId, mall.lastCommand.authorizationSnapshotId());
        assertEquals("service-jwt", mall.lastServiceJwt);
        assertEquals(mall.lastCommand.idempotencyKey(), workflow.idempotencyKey(requestId, card.actionId()));
    }

    @Test
    void materialChangeInvalidatesOldActionId() {
        AfterSaleWorkflow workflow = workflow(new StubMall());
        ConfirmationCard oldCard = workflow.collect(context, AfterSaleIntent.CANCEL_UNPAID,
                Map.of("orderId", "101", "reason", "误下单")).confirmationCard();
        ConfirmationCard newCard = workflow.collect(context, AfterSaleIntent.CANCEL_UNPAID,
                Map.of("orderId", "101", "reason", "地址错误")).confirmationCard();

        assertFalse(oldCard.actionId().equals(newCard.actionId()));
        assertThrows(AfterSaleWorkflowException.class, () -> workflow.confirm(context, oldCard.actionId(), true));
    }

    @Test
    void rejectsPayloadIdentityConflicts() {
        assertThrows(AfterSaleWorkflowException.class, () -> workflow(new StubMall()).collect(context,
                AfterSaleIntent.CANCEL_UNPAID, Map.of("orderId", "101", "tenantId", "other")));
    }

    @Test
    void mallUnavailableDoesNotInventFactsAndHandsOff() {
        StubMall mall = new StubMall();
        mall.unavailable = true;
        AfterSaleResult result = workflow(mall).collect(context, AfterSaleIntent.CANCEL_UNPAID,
                Map.of("orderId", "101", "reason", "误下单"));
        assertEquals(AfterSaleStatus.HUMAN_HANDOFF, result.status());
        assertEquals(null, result.confirmationCard());
    }

    @Test
    void timeoutQueriesStatusBeforeAnyFurtherWriteAndUnknownNeverRetries() {
        StubMall mall = new StubMall();
        mall.submitTimeout = true;
        AfterSaleWorkflow workflow = workflow(mall);
        ConfirmationCard card = workflow.collect(context, AfterSaleIntent.REFUND_PAID,
                Map.of("orderId", "101", "orderItemId", "2", "reason", "质量问题")).confirmationCard();

        assertEquals(AfterSaleStatus.PROCESSING, workflow.confirm(context, card.actionId(), true).status());
        assertEquals(1, mall.submitCalls);
        assertEquals(AfterSaleStatus.MANUAL_RECONCILIATION, workflow.confirm(context, card.actionId(), true).status());
        assertEquals(1, mall.submitCalls);
        assertEquals(1, mall.queryCommandCalls);
    }

    @Test
    void displaysMallStatusesAndManualReconciliationTruthfully() {
        StubMall mall = new StubMall();
        mall.status = AfterSaleStatus.MANUAL_RECONCILIATION;
        AfterSaleResult result = workflow(mall).queryStatus(context, "test-case");
        assertEquals(AfterSaleStatus.MANUAL_RECONCILIATION, result.status());
        assertEquals("需要人工处理", result.message());
    }

    @Test
    void statusIntentQueriesMallCaseWithoutCreatingAConfirmationCard() {
        StubMall mall = new StubMall();
        mall.status = AfterSaleStatus.WAITING_STAFF_APPROVAL;

        AfterSaleResult result = workflow(mall).collect(context, AfterSaleIntent.QUERY_STATUS, Map.of("caseId", "test-case"));

        assertEquals(AfterSaleStatus.WAITING_STAFF_APPROVAL, result.status());
        assertEquals(null, result.confirmationCard());
    }

    private AfterSaleWorkflow workflow(StubMall mall) {
        return new AfterSaleWorkflow(mall, new InMemoryConfirmationStore(), Clock.systemUTC());
    }

    private static final class StubMall implements AfterSaleMallPort {
        private OrderFact fact = new OrderFact("101", "test-p6-b-tenant", "test-member", false,
                new BigDecimal("99.00"), Map.of("2", new OrderItemFact("2", "测试商品", 1)));
        private AfterSaleStatus status = AfterSaleStatus.COMPLETED;
        private boolean unavailable;
        private boolean submitTimeout;
        private int submitCalls;
        private int queryCommandCalls;
        private ControlledAfterSaleCommand lastCommand;
        private String lastServiceJwt;

        public OrderFact findOrder(TrustedAfterSaleContext ignored, String orderId) {
            if (unavailable) throw new MallUnavailableException();
            return fact;
        }
        public AfterSaleStatus submit(TrustedAfterSaleContext ignored, ControlledAfterSaleCommand command) {
            submitCalls++;
            lastCommand = command;
            lastServiceJwt = ignored.serviceJwt();
            if (submitTimeout) throw new MallTimeoutException();
            return AfterSaleStatus.WAITING_STAFF_APPROVAL;
        }
        public AfterSaleStatus queryCommand(TrustedAfterSaleContext ignored, String idempotencyKey) {
            queryCommandCalls++;
            return AfterSaleStatus.UNKNOWN;
        }
        public AfterSaleStatus queryCase(TrustedAfterSaleContext ignored, String caseId) { return status; }
    }
}
