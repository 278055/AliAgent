package com.bn.aliagent.orchestration.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditSanitizerTest {
    @Test
    void removesCredentialsIdentityAndPaymentAddressData() {
        Map<String, Object> sanitized = AuditSanitizer.sanitize(Map.of("jwt", "secret", "address", "road", "amount", 10, "query", "safe"));

        assertFalse(sanitized.containsKey("jwt"));
        assertFalse(sanitized.containsKey("address"));
        assertFalse(sanitized.containsKey("amount"));
    }
}
