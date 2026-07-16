package com.bn.aliagent.conversation.streaming;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.ConversationService;
import com.bn.aliagent.conversation.core.ConversationModels.Message;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.bn.aliagent.conversation.streaming.StreamingModels.Generation;
import static com.bn.aliagent.conversation.streaming.StreamingModels.GenerationStatus;
import static com.bn.aliagent.conversation.streaming.StreamingModels.StreamChunk;
import static com.bn.aliagent.conversation.streaming.StreamingModels.StreamEvent;

public class StreamingService {
    private final ConversationService conversations;
    private final StreamingRepository generations;
    private final DraftStore drafts;
    private final SseEventHub events;

    public StreamingService(ConversationService conversations, StreamingRepository generations, DraftStore drafts) {
        this(conversations, generations, drafts, new SseEventHub());
    }
    public StreamingService(ConversationService conversations, StreamingRepository generations, DraftStore drafts, SseEventHub events) {
        this.conversations = conversations;
        this.generations = generations;
        this.drafts = drafts;
        this.events = events;
    }

    @PostConstruct
    void recoverAfterRestart() { recoverInterrupted(); }

    public void acceptChunk(TrustedConversationRequestContext context, UUID conversationId, UUID generationId, StreamChunk chunk) {
        validate(chunk);
        Message message = publicAiMessage(context, conversationId, generationId, chunk.messageId());
        Generation generation = generations.find(context.tenantId(), conversationId, generationId).orElseGet(() ->
                generations.findByMessage(context.tenantId(), conversationId, chunk.messageId()).orElseGet(() ->
                        new Generation(generationId, context.tenantId(), conversationId, chunk.messageId(), context.requestId(),
                                message.sequence(), GenerationStatus.STREAMING, -1, "", Instant.now())));
        if (generation.status() != GenerationStatus.STREAMING || chunk.chunkIndex() <= generation.lastChunkIndex()) return;
        GenerationStatus status = chunk.finalChunk() ? GenerationStatus.COMPLETED : GenerationStatus.STREAMING;
        Generation updated = new Generation(generation.generationId(), generation.tenantId(), generation.conversationId(), generation.messageId(),
                generation.requestId(), generation.sequence(), status, chunk.chunkIndex(), generation.content() + chunk.delta(), Instant.now());
        generations.save(updated);
        try { drafts.save(updated); } catch (RuntimeException ignored) { /* PostgreSQL 检查点始终是权威事实。 */ }
        events.emit(chunkEvent(updated, chunk));
    }

    public void cancel(TrustedConversationRequestContext context, UUID conversationId, UUID generationId) {
        Generation current = required(context, conversationId, generationId);
        if (current.status() != GenerationStatus.STREAMING) return;
        Generation interrupted = new Generation(current.generationId(), current.tenantId(), current.conversationId(), current.messageId(),
                current.requestId(), current.sequence(), GenerationStatus.INTERRUPTED, current.lastChunkIndex(), current.content(), Instant.now());
        generations.save(interrupted);
        try { drafts.markCancelled(interrupted); } catch (RuntimeException ignored) { /* 持久化终态已先写入。 */ }
        events.emit(terminalEvent(interrupted));
    }

    public List<StreamEvent> replay(TrustedConversationRequestContext context, UUID conversationId, long afterSequence) {
        if (afterSequence < 0) throw new ConversationException("CONV-400-003", "afterSequence must not be negative");
        conversations.messages(context, conversationId, 0, 1);
        return generations.terminalAfter(context.tenantId(), conversationId, afterSequence).stream().map(this::terminalEvent).toList();
    }

    public int recoverInterrupted() {
        List<Generation> active = generations.active();
        active.forEach(value -> generations.save(new Generation(value.generationId(), value.tenantId(), value.conversationId(), value.messageId(),
                value.requestId(), value.sequence(), GenerationStatus.INTERRUPTED, value.lastChunkIndex(), checkpoint(value), Instant.now())));
        return active.size();
    }

    private Generation required(TrustedConversationRequestContext context, UUID conversationId, UUID generationId) {
        return generations.find(context.tenantId(), conversationId, generationId)
                .orElseThrow(() -> new ConversationException("CONV-404-001", "Generation does not exist"));
    }

    private Message publicAiMessage(TrustedConversationRequestContext context, UUID conversationId, UUID generationId, UUID messageId) {
        Message message = conversations.findGeneration(context.tenantId(), conversationId, context.requestId())
                .orElseThrow(() -> new ConversationException("TENANT-403-001", "Generation is not accessible"));
        if (!message.id().equals(messageId) || !"AI".equals(message.senderType()) || !"PUBLIC".equals(message.visibility())
                || !"STREAMING".equals(message.status()) || !generationId.equals(generationId(message))) {
            throw new ConversationException("CONV-409-001", "Generation does not match the persisted AI message");
        }
        return message;
    }

    private UUID generationId(Message message) {
        String marker = "\"generationId\":\"";
        int valueStart = message.metadata().indexOf(marker) + marker.length();
        if (valueStart < marker.length()) throw new ConversationException("CONV-409-001", "Generation metadata is invalid");
        try { return UUID.fromString(message.metadata().substring(valueStart, message.metadata().indexOf('"', valueStart))); }
        catch (IllegalArgumentException | IndexOutOfBoundsException exception) { throw new ConversationException("CONV-409-001", "Generation metadata is invalid"); }
    }

    private String checkpoint(Generation generation) {
        try { return drafts.load(generation).orElse(generation.content()); } catch (RuntimeException ignored) { return generation.content(); }
    }

    private StreamEvent terminalEvent(Generation generation) {
        String type = generation.status() == GenerationStatus.COMPLETED ? "message.completed" : "message.interrupted";
        return new StreamEvent(type, generation.tenantId(), generation.conversationId(), generation.messageId(), generation.sequence(),
                generation.requestId(), generation.updatedAt(), null, null, generation.status().name());
    }

    private StreamEvent chunkEvent(Generation generation, StreamChunk chunk) {
        if (generation.status() != GenerationStatus.STREAMING) return terminalEvent(generation);
        return new StreamEvent("message.delta", generation.tenantId(), generation.conversationId(), generation.messageId(), generation.sequence(),
                generation.requestId(), generation.updatedAt(), chunk.delta(), chunk.chunkIndex(), null);
    }

    private void validate(StreamChunk chunk) {
        if (chunk.messageId() == null || chunk.chunkIndex() < 0 || chunk.delta() == null) {
            throw new ConversationException("CONV-400-004", "Invalid stream chunk");
        }
    }
}
