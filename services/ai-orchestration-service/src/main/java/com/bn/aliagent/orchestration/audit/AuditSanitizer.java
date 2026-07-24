package com.bn.aliagent.orchestration.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AuditSanitizer {
    private static final String[] SENSITIVE = { "jwt", "authorization", "token", "identity", "payment", "amount", "card", "address", "phone" };
    private AuditSanitizer() { }
    public static Map<String, Object> sanitize(Map<String, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (!sensitive(key)) result.put(key, value); });
        return Map.copyOf(result);
    }
    private static boolean sensitive(String key) { String lower = key.toLowerCase(Locale.ROOT); for (String word : SENSITIVE) if (lower.contains(word)) return true; return false; }
}
