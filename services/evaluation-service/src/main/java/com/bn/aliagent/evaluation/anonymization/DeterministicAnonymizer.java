package com.bn.aliagent.evaluation.anonymization;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DeterministicAnonymizer {
    private final byte[] key;
    private final AnonymizationPolicy policy;

    public DeterministicAnonymizer(String key) { this(key, AnonymizationPolicy.v1()); }
    public DeterministicAnonymizer(String key, AnonymizationPolicy policy) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("匿名化密钥不能为空");
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.policy = policy;
    }

    public AnonymizationResult anonymize(Map<String, Object> payload, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new SecurityException("缺少可信租户");
        Map<String, Object> sanitized = new LinkedHashMap<>();
        boolean quarantine = sanitizeMap(payload, sanitized, tenantId);
        String json = canonicalJson(sanitized);
        return new AnonymizationResult(quarantine ? AnonymizationStatus.QUARANTINED : AnonymizationStatus.SAFE,
                quarantine ? "{}" : json, policy.ruleVersion(), sha256(json));
    }

    @SuppressWarnings("unchecked")
    private boolean sanitizeMap(Map<String, Object> source, Map<String, Object> target, String tenantId) {
        boolean quarantine = false;
        for (var entry : source.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (policy.sensitiveKeys().contains(name.toLowerCase())) continue;
            if (policy.correlationKeys().contains(name)) { target.put(name, "ref-" + hmac(tenantId + ":" + value)); continue; }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                quarantine |= sanitizeMap((Map<String, Object>) map, nested, tenantId);
                target.put(name, nested);
            } else if (value instanceof List<?> list) {
                List<Object> cleaned = new ArrayList<>();
                for (Object item : list) cleaned.add(item instanceof String text ? scrub(text) : item);
                target.put(name, cleaned);
            } else if (value instanceof String text) {
                if (text.contains("PRIVATE KEY")) quarantine = true;
                target.put(name, scrub(text));
            } else target.put(name, value);
        }
        return quarantine;
    }

    private String scrub(String value) {
        return value.replaceAll("(?i)bearer\\s+[^\\s,]+", "[REDACTED]")
                .replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[REDACTED]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[REDACTED]")
                .replaceAll("(?<!\\d)\\d{17}[0-9Xx](?!\\d)", "[REDACTED]")
                .replaceAll("[^，,。;；]*?(?:省|市|区|县|路|街|号)[^，,。;；]*", "[REDACTED]");
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("HMAC-SHA256 不可用", ex); }
    }

    private static String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException("SHA-256 不可用", ex); }
    }

    @SuppressWarnings("unchecked")
    private static String canonicalJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + canonicalJson(entry.getValue())).reduce("{", (left, right) -> left.equals("{") ? left + right : left + "," + right) + "}";
        }
        if (value instanceof List<?> list) return list.stream().map(DeterministicAnonymizer::canonicalJson).reduce("[", (left, right) -> left.equals("[") ? left + right : left + "," + right) + "]";
        if (value instanceof String text) return quote(text);
        return String.valueOf(value);
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
