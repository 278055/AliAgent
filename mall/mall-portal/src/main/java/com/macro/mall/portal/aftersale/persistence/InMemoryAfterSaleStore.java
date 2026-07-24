package com.macro.mall.portal.aftersale.persistence;

import com.macro.mall.portal.aftersale.core.AfterSaleCase;
import com.macro.mall.portal.aftersale.core.AfterSaleStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 事务边界的内存实现，用于无需外部系统的领域测试。 */
public final class InMemoryAfterSaleStore {
    private Map<String, AfterSaleCase> cases = new HashMap<>();
    private final Map<String, String> commandResults = new HashMap<>();
    private final Map<String, String> idempotencyResults = new HashMap<>();
    private final List<String> outbox = new ArrayList<>();
    private final Set<String> inbox = new HashSet<>();
    private final List<String> audits = new ArrayList<>();
    private int sagaAdvances;
    private boolean failNextTransaction;

    public synchronized void transaction(Runnable work) {
        Map<String, AfterSaleCase> beforeCases = new HashMap<>(cases);
        Map<String, String> beforeCommands = new HashMap<>(commandResults);
        Map<String, String> beforeIdempotency = new HashMap<>(idempotencyResults);
        List<String> beforeOutbox = new ArrayList<>(outbox);
        Set<String> beforeInbox = new HashSet<>(inbox);
        List<String> beforeAudits = new ArrayList<>(audits);
        int beforeAdvances = sagaAdvances;
        try {
            work.run();
            if (failNextTransaction) {
                failNextTransaction = false;
                throw new IllegalStateException("transaction failed");
            }
        } catch (RuntimeException exception) {
            cases = beforeCases;
            commandResults.clear(); commandResults.putAll(beforeCommands);
            idempotencyResults.clear(); idempotencyResults.putAll(beforeIdempotency);
            outbox.clear(); outbox.addAll(beforeOutbox);
            inbox.clear(); inbox.addAll(beforeInbox);
            audits.clear(); audits.addAll(beforeAudits);
            sagaAdvances = beforeAdvances;
            throw exception;
        }
    }

    public AfterSaleCase findCase(String caseId) { return cases.get(caseId); }
    public void save(AfterSaleCase value) { cases.put(value.caseId(), value); }
    public String commandResult(String commandId) { return commandResults.get(commandId); }
    public String idempotencyResult(String key) { return idempotencyResults.get(key); }
    public void remember(String commandId, String idempotencyKey, String caseId) { commandResults.put(commandId, caseId); idempotencyResults.put(idempotencyKey, caseId); }
    public boolean hasActiveOrder(Long orderId) { return cases.values().stream().anyMatch(value -> value.orderId().equals(orderId) && active(value.status())); }
    public void event(String event) { outbox.add(event); }
    public boolean claimInbox(String eventId, String consumer) { return inbox.add(eventId + ':' + consumer); }
    public void audit(String action) { audits.add(action); }
    public void advanceSaga() { sagaAdvances++; }
    public int caseCount() { return cases.size(); }
    public int outboxSize() { return outbox.size(); }
    public int auditFailureCount() { return (int) audits.stream().filter(value -> value.startsWith("REJECTED")).count(); }
    public int sagaAdvances() { return sagaAdvances; }
    public void failNextTransaction() { failNextTransaction = true; }

    private static boolean active(AfterSaleStatus status) {
        return status != AfterSaleStatus.REJECTED && status != AfterSaleStatus.COMPLETED && status != AfterSaleStatus.FAILED
                && status != AfterSaleStatus.MANUAL_RECONCILIATION && status != AfterSaleStatus.CANCELLED;
    }
}
