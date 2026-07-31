package com.bn.aliagent.insight.fact;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DeterministicFactAnonymizer {
    private final byte[] key;
    private final InsightAnonymizationPolicy policy;

    public DeterministicFactAnonymizer(String key) { this(key, InsightAnonymizationPolicy.v1()); }
    public DeterministicFactAnonymizer(String key, InsightAnonymizationPolicy policy) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("匿名化密钥不能为空");
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.policy = policy;
    }

    public Result anonymize(Map<String, Object> payload, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new SecurityException("缺少可信租户");
        Map<String, Object> sanitized = new LinkedHashMap<>();
        boolean quarantined = sanitizeMap(payload, sanitized, tenantId);
        return new Result(quarantined ? "{}" : canonicalJson(sanitized), policy.ruleVersion(), quarantined);
    }

    @SuppressWarnings("unchecked")
    private boolean sanitizeMap(Map<String, Object> source, Map<String, Object> target, String tenantId) {
        boolean quarantined = false;
        for (var entry : source.entrySet()) {
            String name = entry.getKey();
            String normalized = name.toLowerCase();
            Object value = entry.getValue();
            if (policy.sensitiveKeys().contains(normalized)) continue;
            if (policy.correlationKeys().contains(normalized)) { target.put(name, "ref-" + hmac(tenantId + ":" + value)); continue; }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                quarantined |= sanitizeMap((Map<String, Object>) map, nested, tenantId);
                target.put(name, nested);
            } else if (value instanceof List<?> list) {
                List<Object> clean = new ArrayList<>();
                for (Object item : list) clean.add(item instanceof String text ? scrub(text) : item);
                target.put(name, clean);
            } else if (value instanceof String text) {
                if (text.contains("PRIVATE KEY")) quarantined = true;
                target.put(name, scrub(text));
            } else target.put(name, value);
        }
        return quarantined;
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
        } catch (GeneralSecurityException exception) { throw new IllegalStateException("HMAC-SHA256 不可用", exception); }
    }

    @SuppressWarnings("unchecked")
    private static String canonicalJson(Object value) {
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + canonicalJson(entry.getValue()))
                .reduce("{", (left, right) -> left.equals("{") ? left + right : left + "," + right) + "}";
        if (value instanceof List<?> list) return list.stream().map(DeterministicFactAnonymizer::canonicalJson)
                .reduce("[", (left, right) -> left.equals("[") ? left + right : left + "," + right) + "]";
        if (value instanceof String text) return quote(text);
        return String.valueOf(value);
    }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    public record Result(String canonicalJson, String ruleVersion, boolean quarantined) { }
}
