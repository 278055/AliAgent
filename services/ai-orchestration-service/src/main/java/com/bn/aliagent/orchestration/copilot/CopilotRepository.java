package com.bn.aliagent.orchestration.copilot;

import java.util.*;

public interface CopilotRepository {
    boolean claimInbox(UUID eventId);
    void completeInbox(UUID eventId);
    Optional<CopilotModels.Suggestion> findByRequestId(UUID requestId);
    Optional<CopilotModels.Suggestion> findSuggestion(UUID suggestionId);
    default List<CopilotModels.Suggestion> findForConversation(String tenantId, UUID conversationId, String agentId) {
        return List.of();
    }
    void save(CopilotModels.Suggestion suggestion, UUID requestId);
    Optional<CopilotModels.Action> findAction(UUID requestId);
    void saveAction(CopilotModels.Action action);
    final class InMemory implements CopilotRepository {
        private final Set<UUID> inbox = new HashSet<>();
        private final Map<UUID, CopilotModels.Suggestion> byRequest = new HashMap<>();
        private final Map<UUID, CopilotModels.Suggestion> suggestions = new HashMap<>();
        private final Map<UUID, CopilotModels.Action> actions = new HashMap<>();
        public synchronized boolean claimInbox(UUID eventId) { return inbox.add(eventId); }
        public void completeInbox(UUID eventId) { }
        public synchronized Optional<CopilotModels.Suggestion> findByRequestId(UUID requestId) { return Optional.ofNullable(byRequest.get(requestId)); }
        public synchronized Optional<CopilotModels.Suggestion> findSuggestion(UUID id) { return Optional.ofNullable(suggestions.get(id)); }
        public synchronized List<CopilotModels.Suggestion> findForConversation(String tenantId, UUID conversationId, String agentId) {
            return suggestions.values().stream().filter(value -> tenantId.equals(value.tenantId())
                    && conversationId.equals(value.conversationId()) && agentId.equals(value.assignedAgentId())).toList();
        }
        public synchronized void save(CopilotModels.Suggestion value, UUID requestId) { suggestions.put(value.suggestionId(), value); byRequest.put(requestId, value); }
        public synchronized Optional<CopilotModels.Action> findAction(UUID requestId) { return Optional.ofNullable(actions.get(requestId)); }
        public synchronized void saveAction(CopilotModels.Action action) { actions.put(action.requestId(), action); }
    }
}
