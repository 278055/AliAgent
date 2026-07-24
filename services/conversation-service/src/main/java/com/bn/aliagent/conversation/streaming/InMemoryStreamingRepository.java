package com.bn.aliagent.conversation.streaming;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InMemoryStreamingRepository implements StreamingRepository {
    private final Map<UUID, StreamingModels.Generation> generations = new HashMap<>();

    public Optional<StreamingModels.Generation> find(String tenantId, UUID conversationId, UUID generationId) {
        return Optional.ofNullable(generations.get(generationId)).filter(value -> value.tenantId().equals(tenantId) && value.conversationId().equals(conversationId));
    }
    public Optional<StreamingModels.Generation> findByMessage(String tenantId, UUID conversationId, UUID messageId) {
        return generations.values().stream().filter(value -> value.tenantId().equals(tenantId) && value.conversationId().equals(conversationId) && value.messageId().equals(messageId)).findFirst();
    }
    public StreamingModels.Generation save(StreamingModels.Generation generation) { generations.put(generation.generationId(), generation); return generation; }
    public List<StreamingModels.Generation> terminalAfter(String tenantId, UUID conversationId, long afterSequence) {
        return generations.values().stream().filter(value -> value.tenantId().equals(tenantId) && value.conversationId().equals(conversationId) && value.sequence() > afterSequence && value.status() != StreamingModels.GenerationStatus.STREAMING).sorted(Comparator.comparingLong(StreamingModels.Generation::sequence)).toList();
    }
    public List<StreamingModels.Generation> active() { return new ArrayList<>(generations.values()).stream().filter(value -> value.status() == StreamingModels.GenerationStatus.STREAMING).toList(); }
}
