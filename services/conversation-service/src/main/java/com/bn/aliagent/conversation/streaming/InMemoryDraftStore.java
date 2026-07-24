package com.bn.aliagent.conversation.streaming;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class InMemoryDraftStore implements DraftStore {
    private final Map<String, String> values = new HashMap<>();
    public void save(StreamingModels.Generation generation) { values.put(key(generation), generation.content()); }
    public void markCancelled(StreamingModels.Generation generation) { values.put(key(generation) + ":cancelled", "true"); }
    public Optional<String> load(StreamingModels.Generation generation) { return Optional.ofNullable(values.get(key(generation))); }
    private String key(StreamingModels.Generation value) { return "conversation:" + value.tenantId() + ":" + value.conversationId() + ":generation:" + value.generationId() + ":draft"; }
}
