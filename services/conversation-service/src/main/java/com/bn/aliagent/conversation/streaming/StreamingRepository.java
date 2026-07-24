package com.bn.aliagent.conversation.streaming;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StreamingRepository {
    Optional<StreamingModels.Generation> find(String tenantId, UUID conversationId, UUID generationId);
    Optional<StreamingModels.Generation> findByMessage(String tenantId, UUID conversationId, UUID messageId);
    StreamingModels.Generation save(StreamingModels.Generation generation);
    List<StreamingModels.Generation> terminalAfter(String tenantId, UUID conversationId, long afterSequence);
    List<StreamingModels.Generation> active();
}
