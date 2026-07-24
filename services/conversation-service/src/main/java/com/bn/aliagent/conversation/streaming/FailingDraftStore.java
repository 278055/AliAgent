package com.bn.aliagent.conversation.streaming;

import java.util.Optional;

public final class FailingDraftStore implements DraftStore {
    public void save(StreamingModels.Generation generation) { throw new IllegalStateException("Redis unavailable"); }
    public void markCancelled(StreamingModels.Generation generation) { throw new IllegalStateException("Redis unavailable"); }
    public Optional<String> load(StreamingModels.Generation generation) { throw new IllegalStateException("Redis unavailable"); }
}
