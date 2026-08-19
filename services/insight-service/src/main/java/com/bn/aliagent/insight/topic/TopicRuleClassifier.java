package com.bn.aliagent.insight.topic;

import java.util.Map;

public final class TopicRuleClassifier {
    private static final Map<TopicRisk, String[]> KEYWORDS = Map.of(
            TopicRisk.PRIVACY, new String[]{"隐私", "手机号", "地址", "身份证"},
            TopicRisk.FRAUD, new String[]{"诈骗", "欺诈", "骗"},
            TopicRisk.REGULATORY, new String[]{"监管", "合规", "投诉"},
            TopicRisk.FUNDS, new String[]{"资金", "付款", "退款"});

    public TopicRisk classify(String anonymizedText) {
        return KEYWORDS.entrySet().stream()
                .filter(entry -> java.util.Arrays.stream(entry.getValue()).anyMatch(anonymizedText::contains))
                .map(Map.Entry::getKey).findFirst().orElse(TopicRisk.NORMAL);
    }
}
