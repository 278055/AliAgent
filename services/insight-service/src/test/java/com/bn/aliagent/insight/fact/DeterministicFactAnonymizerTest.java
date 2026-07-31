package com.bn.aliagent.insight.fact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DeterministicFactAnonymizerTest {
    private final DeterministicFactAnonymizer anonymizer = new DeterministicFactAnonymizer("test-key-for-p9-anonymization");

    @Test
    void 清理敏感内容并创建租户级引用() {
        var result = anonymizer.anonymize(Map.of("orderId", "ORDER-1", "phone", "13800138000", "text",
                "联系 test@example.com，地址北京市朝阳区测试路1号"), "test-p9-a");

        assertFalse(result.canonicalJson().contains("13800138000"));
        assertFalse(result.canonicalJson().contains("test@example.com"));
        assertFalse(result.canonicalJson().contains("测试路1号"));
        assertTrue(result.canonicalJson().contains("ref-"));
        assertEquals("insight-anon-v1", result.ruleVersion());
    }

    @Test
    void 相同标识在不同租户不可反解且私钥隔离() {
        String a = anonymizer.anonymize(Map.of("userId", "user-1"), "test-a").canonicalJson();
        String b = anonymizer.anonymize(Map.of("userId", "user-1"), "test-b").canonicalJson();

        assertFalse(a.equals(b));
        assertTrue(anonymizer.anonymize(Map.of("text", "-----BEGIN PRIVATE KEY-----"), "test-a").quarantined());
    }
}
