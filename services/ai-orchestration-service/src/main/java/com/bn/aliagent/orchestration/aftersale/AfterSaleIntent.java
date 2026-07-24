package com.bn.aliagent.orchestration.aftersale;

public enum AfterSaleIntent {
    CANCEL_UNPAID, REFUND_PAID, RETURN_REFUND, QUERY_STATUS, SUPPLEMENT_MATERIAL, HUMAN_HANDOFF, CLARIFY;

    public static AfterSaleIntent classify(String input) {
        String value = input == null ? "" : input;
        if (value.contains("人工") || value.contains("客服")) return HUMAN_HANDOFF;
        if (value.contains("进度") || value.contains("状态")) return QUERY_STATUS;
        if (value.contains("补充") && value.contains("材料")) return SUPPLEMENT_MATERIAL;
        if (value.contains("退货")) return RETURN_REFUND;
        if (value.contains("退款")) return REFUND_PAID;
        if (value.contains("取消") && value.contains("订单")) return CANCEL_UNPAID;
        return CLARIFY;
    }

    public static boolean isAfterSale(String input) {
        String value = input == null ? "" : input;
        return value.contains("售后") || value.contains("退款") || value.contains("退货") || value.contains("取消订单")
                || value.contains("人工客服");
    }
}
