package com.bn.aliagent.conversation.streaming;

import java.util.Optional;

public interface DraftStore {
    void save(StreamingModels.Generation generation);
    void markCancelled(StreamingModels.Generation generation);
    Optional<String> load(StreamingModels.Generation generation);
}
