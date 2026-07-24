package com.bn.aliagent.orchestration.confirmation;

import java.math.BigDecimal;

/** 供用户确认的只读快照，金额和审批提示均来自 mall 事实。 */
public record ConfirmationCard(
        String actionId,
        String applicationType,
        String orderId,
        String orderItemId,
        String reason,
        BigDecimal amountFact,
        boolean staffApprovalRequired,
        boolean supervisorApprovalRequired,
        String riskNotice) { }
