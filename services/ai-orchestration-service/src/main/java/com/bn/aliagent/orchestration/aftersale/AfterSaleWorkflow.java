package com.bn.aliagent.orchestration.aftersale;

import com.bn.aliagent.orchestration.confirmation.ConfirmationCard;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅编排确认和 mall 命令；订单、金额、支付和处理结果始终由 mall 提供。 */
public final class AfterSaleWorkflow {
    private static final BigDecimal SUPERVISOR_AMOUNT = new BigDecimal("500.00");
    private final AfterSaleMallPort mall;
    private final InMemoryConfirmationStore confirmations;
    private final Clock clock;

    public AfterSaleWorkflow(AfterSaleMallPort mall, InMemoryConfirmationStore confirmations, Clock clock) {
        this.mall = mall;
        this.confirmations = confirmations;
        this.clock = clock;
    }

    public AfterSaleResult collect(TrustedAfterSaleContext context, AfterSaleIntent intent, Map<String, String> materials) {
        validateIdentity(context, materials);
        materials = confirmations.mergeMaterials(context.requestId().toString(), materials);
        List<String> missing = missing(intent, materials);
        if (!missing.isEmpty()) return new AfterSaleResult(AfterSaleStatus.WAITING_USER_CONFIRMATION, missing, null, "请补充必要材料");
        if (intent == AfterSaleIntent.QUERY_STATUS) return queryStatus(context, materials.get("caseId"));
        if (intent == AfterSaleIntent.HUMAN_HANDOFF || intent == AfterSaleIntent.CLARIFY) {
            return new AfterSaleResult(AfterSaleStatus.HUMAN_HANDOFF, List.of(), null, "已为您转人工处理");
        }
        OrderFact order;
        try {
            order = mall.findOrder(context, materials.get("orderId"));
        } catch (MallUnavailableException exception) {
            return new AfterSaleResult(AfterSaleStatus.HUMAN_HANDOFF, List.of(), null, "暂时无法确认售后事实，已转人工处理");
        }
        verifyOwnership(context, order);
        String itemId = materials.getOrDefault("orderItemId", "");
        if (!itemId.isEmpty() && !order.items().containsKey(itemId)) throw new AfterSaleWorkflowException("订单项不属于该订单");
        confirmations.invalidateForRequest(context.requestId().toString());
        ConfirmationCard card = card(intent, order, materials, itemId);
        confirmations.save(new InMemoryConfirmationStore.PendingConfirmation(context.requestId().toString(), card,
                command(context, card, materials), false, null));
        return new AfterSaleResult(AfterSaleStatus.WAITING_USER_CONFIRMATION, List.of(), card, "请确认售后申请");
    }

    public AfterSaleResult confirm(TrustedAfterSaleContext context, String actionId, boolean confirmed) {
        InMemoryConfirmationStore.PendingConfirmation pending = confirmations.find(actionId)
                .orElseThrow(() -> new AfterSaleWorkflowException("确认卡已失效"));
        if (!pending.requestId().equals(context.requestId().toString())) throw new AfterSaleWorkflowException("确认卡不属于当前请求");
        if (!confirmed) return new AfterSaleResult(AfterSaleStatus.HUMAN_HANDOFF, List.of(), null, "已取消本次售后申请");
        if (pending.submittedStatus() != null) return new AfterSaleResult(pending.submittedStatus(), List.of(), null, message(pending.submittedStatus()));
        if (pending.timedOut()) {
            AfterSaleStatus status = mall.queryCommand(context, pending.command().idempotencyKey());
            return status == AfterSaleStatus.UNKNOWN
                    ? new AfterSaleResult(AfterSaleStatus.MANUAL_RECONCILIATION, List.of(), null, "处理状态未知，已转人工对账")
                    : new AfterSaleResult(status, List.of(), null, message(status));
        }
        try {
            AfterSaleStatus status = mall.submit(context, pending.command());
            confirmations.save(pending.submitted(status));
            return new AfterSaleResult(status, List.of(), null, message(status));
        } catch (MallTimeoutException exception) {
            confirmations.save(pending.timeout());
            return new AfterSaleResult(AfterSaleStatus.PROCESSING, List.of(), null, "提交结果处理中，请稍后查询");
        } catch (MallUnavailableException exception) {
            return new AfterSaleResult(AfterSaleStatus.HUMAN_HANDOFF, List.of(), null, "暂时无法提交售后申请，已转人工处理");
        }
    }

    public AfterSaleResult queryStatus(TrustedAfterSaleContext context, String caseId) {
        try {
            AfterSaleStatus status = mall.queryCase(context, caseId);
            return new AfterSaleResult(status, List.of(), null, message(status));
        } catch (MallUnavailableException exception) {
            return new AfterSaleResult(AfterSaleStatus.HUMAN_HANDOFF, List.of(), null, "暂时无法查询售后状态，已转人工处理");
        }
    }

    public String idempotencyKey(UUID requestId, String actionId) {
        return UUID.nameUUIDFromBytes((requestId + ":" + actionId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ConfirmationCard card(AfterSaleIntent intent, OrderFact order, Map<String, String> materials, String itemId) {
        boolean staff = intent == AfterSaleIntent.REFUND_PAID || intent == AfterSaleIntent.RETURN_REFUND;
        boolean supervisor = staff && order.amount().compareTo(SUPERVISOR_AMOUNT) >= 0;
        String actionId = UUID.randomUUID().toString();
        return new ConfirmationCard(actionId, intent.name(), order.orderId(), itemId, materials.get("reason"), order.amount(),
                staff, supervisor, supervisor ? "金额达到500.00，需要主管审批" : staff ? "需要客服审批" : "mall 将重新校验订单资格");
    }

    private ControlledAfterSaleCommand command(TrustedAfterSaleContext context, ConfirmationCard card, Map<String, String> materials) {
        return new ControlledAfterSaleCommand(UUID.randomUUID(), commandType(card.applicationType()), 1, Instant.now(clock),
                context.tenantId(), context.traceId(), context.requestId(), idempotencyKey(context.requestId(), card.actionId()),
                context.actorId(), context.actorType(), context.authorizationSnapshotId(), Map.of(
                "orderId", card.orderId(), "orderItemId", card.orderItemId(), "reason", card.reason(),
                "quantity", materials.getOrDefault("quantity", "1"), "materialReference", materials.getOrDefault("materialReference", "")));
    }

    private static String commandType(String applicationType) {
        return switch (AfterSaleIntent.valueOf(applicationType)) {
            case CANCEL_UNPAID -> "CancelOrderRequested";
            case RETURN_REFUND -> "ReturnRefundRequested";
            default -> "RefundRequested";
        };
    }

    private static List<String> missing(AfterSaleIntent intent, Map<String, String> value) {
        List<String> fields = new ArrayList<>();
        if (intent == AfterSaleIntent.QUERY_STATUS) {
            if (blank(value, "caseId")) fields.add("caseId");
            return fields;
        }
        if (intent == AfterSaleIntent.HUMAN_HANDOFF || intent == AfterSaleIntent.CLARIFY) return fields;
        if (blank(value, "orderId")) fields.add("orderId");
        if (intent != AfterSaleIntent.CANCEL_UNPAID && blank(value, "orderItemId")) fields.add("orderItemId");
        if (blank(value, "reason")) fields.add("reason");
        return fields;
    }

    private static boolean blank(Map<String, String> value, String key) { return value.get(key) == null || value.get(key).isBlank(); }
    private static void validateIdentity(TrustedAfterSaleContext context, Map<String, String> materials) {
        if (materials.containsKey("tenantId") && !context.tenantId().equals(materials.get("tenantId"))) throw new AfterSaleWorkflowException("租户不一致");
        if (materials.containsKey("actorId") && !context.actorId().equals(materials.get("actorId"))) throw new AfterSaleWorkflowException("主体不一致");
    }
    private static void verifyOwnership(TrustedAfterSaleContext context, OrderFact order) {
        if (!context.tenantId().equals(order.tenantId()) || !context.actorId().equals(order.actorId())) throw new AfterSaleWorkflowException("订单归属不匹配");
    }
    private static String message(AfterSaleStatus status) {
        return switch (status) {
            case MANUAL_RECONCILIATION -> "需要人工处理";
            case WAITING_STAFF_APPROVAL -> "等待客服审批";
            case WAITING_SUPERVISOR_APPROVAL -> "等待主管审批";
            case EXECUTING, PROCESSING, RETRY_PENDING, UNKNOWN -> "处理中";
            case COMPLETED -> "售后已完成";
            case FAILED -> "售后处理失败";
            default -> "售后状态已更新";
        };
    }
}
