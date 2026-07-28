package com.bn.aliagent.evaluation.anonymization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeterministicAnonymizerTest {
    @Test
    void removesSensitiveFieldsBeforeCandidatePersistence() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", "user-1");
        payload.put("phone", "13800138000");
        payload.put("message", "联系 test@example.com，地址北京市朝阳区，身份证 11010519491231002X，Bearer secret-token");

        var result = new DeterministicAnonymizer("tenant-key").anonymize(payload, "test-p8-a-tenant");

        assertEquals(AnonymizationStatus.SAFE, result.status());
        assertFalse(result.canonicalJson().contains("13800138000"));
        assertFalse(result.canonicalJson().contains("test@example.com"));
        assertFalse(result.canonicalJson().contains("Bearer"));
        assertFalse(result.canonicalJson().contains("user-1"));
        assertEquals("anon-v1", result.ruleVersion());
    }

    @Test
    void quarantinesTextThatCannotBeReliablyMinimized() {
        var result = new DeterministicAnonymizer("tenant-key").anonymize(Map.of("message", "BEGIN PRIVATE KEY"), "test-p8-a-tenant");

        assertEquals(AnonymizationStatus.QUARANTINED, result.status());
        assertFalse(result.canonicalJson().contains("PRIVATE KEY"));
    }

    @Test
    void publicSharingRequiresExplicitAuthorizationAndRegeneratesIdentifiers() {
        var privateResult = new DeterministicAnonymizer("tenant-key").anonymize(Map.of("userId", "user-1"), "test-p8-a-tenant");
        var publicAnonymizer = new PublicDatasetAnonymizer(new DeterministicAnonymizer("public-key"));

        assertThrows(SecurityException.class, () -> publicAnonymizer.anonymize(Map.of("userId", "user-1"), "test-p8-a-tenant", DatasetShareAuthorization.inactive()));
        var publicResult = publicAnonymizer.anonymize(Map.of("userId", "user-1"), "test-p8-a-tenant",
                DatasetShareAuthorization.active("admin-1", Instant.parse("2026-07-28T00:00:00Z")));

        assertEquals(AnonymizationStatus.SAFE, publicResult.status());
        assertNotEquals(privateResult.canonicalJson(), publicResult.canonicalJson());
    }
}
