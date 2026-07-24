package com.bn.aliagent.orchestration.aftersale;

import com.bn.aliagent.orchestration.confirmation.ConfirmationCard;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryConfirmationStore {
    private final Map<String, PendingConfirmation> cards = new HashMap<>();
    private final Map<String, Map<String, String>> materials = new HashMap<>();

    public void save(PendingConfirmation value) { cards.put(value.card().actionId(), value); }
    public Optional<PendingConfirmation> find(String actionId) { return Optional.ofNullable(cards.get(actionId)); }
    public Map<String, String> mergeMaterials(String requestId, Map<String, String> next) {
        Map<String, String> merged = new HashMap<>(materials.getOrDefault(requestId, Map.of()));
        merged.putAll(next);
        materials.put(requestId, Map.copyOf(merged));
        return Map.copyOf(merged);
    }
    public void invalidateForRequest(String requestId) { cards.values().removeIf(value -> value.requestId().equals(requestId)); }

    public record PendingConfirmation(String requestId, ConfirmationCard card, ControlledAfterSaleCommand command,
                                      boolean timedOut, AfterSaleStatus submittedStatus) {
        PendingConfirmation timeout() { return new PendingConfirmation(requestId, card, command, true, null); }
        PendingConfirmation submitted(AfterSaleStatus status) { return new PendingConfirmation(requestId, card, command, false, status); }
    }
}
