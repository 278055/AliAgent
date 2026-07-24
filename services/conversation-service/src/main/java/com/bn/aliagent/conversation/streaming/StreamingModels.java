package com.bn.aliagent.conversation.streaming;

import java.time.Instant;
import java.util.UUID;

public final class StreamingModels {
    private StreamingModels() { }

    public enum GenerationStatus { STREAMING, COMPLETED, INTERRUPTED }

    public record Generation(UUID generationId, String tenantId, UUID conversationId, UUID messageId,
                             UUID requestId, long sequence, GenerationStatus status, int lastChunkIndex,
                             String content, Instant updatedAt) { }

    public record StreamChunk(UUID messageId, int chunkIndex, String delta, boolean finalChunk, String finishReason) { }

    public record StreamEvent(String eventType, String tenantId, UUID conversationId, UUID messageId,
                              long sequence, UUID requestId, Instant occurredAt, String delta,
                              Integer chunkIndex, String status) { }
}
