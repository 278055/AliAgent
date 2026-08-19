package com.bn.aliagent.insight.topic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DashScopeTopicNamingPortTest {
    @Test
    void parsesModelNameAndSummaryWithoutSendingOriginalFacts() {
        var port = new DashScopeTopicNamingPort("test-key", "https://test.invalid", body -> {
            if (body.contains("orderId") || body.contains("phone")) throw new AssertionError("请求不得含敏感字段");
            return "{\"output\":{\"text\":\"{\\\"name\\\":\\\"物流延迟\\\",\\\"summary\\\":\\\"配送超时\\\"}\"}}";
        });

        var result = port.name("test-tenant", List.of("匿名物流异常样本"), "qwen-plus", "p9-v1");

        assertEquals("物流延迟", result.displayName());
        assertEquals("配送超时", result.summary());
    }

    @Test
    void rejectsBlankApiKeyBeforeNetworkCall() {
        var port = new DashScopeTopicNamingPort("", "https://test.invalid", body -> "{}");

        assertThrows(IllegalStateException.class, () -> port.name("test-tenant", List.of("匿名样本"), "qwen-plus", "p9-v1"));
    }
}
