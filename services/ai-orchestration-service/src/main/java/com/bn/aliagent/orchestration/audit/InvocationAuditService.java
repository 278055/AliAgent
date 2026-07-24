package com.bn.aliagent.orchestration.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InvocationAuditService {
    private final List<InvocationAudit> records = new ArrayList<>();
    public synchronized void recordTool(UUID executionId, String tenantId, String toolType, String toolName, String outcome,
                                        long latencyMs, String degradationReason, Map<String, ?> attributes) {
        records.add(new InvocationAudit(executionId, tenantId, toolType + ':' + toolName, outcome, latencyMs, degradationReason,
                AuditSanitizer.sanitize(attributes), Instant.now()));
    }
    public synchronized List<InvocationAudit> records() { return List.copyOf(records); }
}
