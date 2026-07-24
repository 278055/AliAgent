package com.bn.aliagent.orchestration.quota;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class TenantQuotaGate {
    private final int perMinute;
    private final int concurrent;
    private final Map<String, Counter> counters = new HashMap<>();
    public TenantQuotaGate(@Value("${orchestration.quota.tenant-per-minute:60}") int perMinute,
                           @Value("${orchestration.quota.tenant-concurrent:4}") int concurrent) {
        this.perMinute = perMinute; this.concurrent = concurrent;
    }

    public synchronized AdmissionResult acquire(String tenantId, ScenarioPriority priority) {
        Counter counter = counters.computeIfAbsent(tenantId, key -> new Counter());
        Instant minute = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        if (!minute.equals(counter.minute)) { counter.minute = minute; counter.used = 0; }
        if (counter.used >= perMinute) return AdmissionResult.QUOTA_EXHAUSTED;
        int normalLimit = Math.max(1, concurrent - 1);
        if (counter.running >= concurrent || (priority == ScenarioPriority.NORMAL && counter.running >= normalLimit)) return AdmissionResult.CONCURRENCY_LIMITED;
        counter.used++; counter.running++; return AdmissionResult.ADMITTED;
    }
    public synchronized void release(String tenantId) { Counter counter = counters.get(tenantId); if (counter != null && counter.running > 0) counter.running--; }
    private static final class Counter { private Instant minute = Instant.EPOCH; private int used; private int running; }
}
