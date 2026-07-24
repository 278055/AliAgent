package com.bn.aliagent.orchestration.routing;

import java.util.List;

public final class RuleFirstIntentRouter {
    private static final List<String> WRITE_INTENT_WORDS = List.of("退款", "退货", "售后", "取消订单", "取消 订单", "改价", "投诉");
    private final IntentClassifier fallback;

    public RuleFirstIntentRouter(IntentClassifier fallback) {
        this.fallback = fallback;
    }

    public Intent route(String input) {
        String value = input == null ? "" : input;
        if (WRITE_INTENT_WORDS.stream().anyMatch(value::contains)) return Intent.HUMAN_HANDOFF;
        if (value.contains("物流") || value.contains("快递") || value.contains("配送")) return Intent.LOGISTICS_QUERY;
        if (value.contains("订单")) return Intent.ORDER_QUERY;
        return fallback.classify(value);
    }
}
