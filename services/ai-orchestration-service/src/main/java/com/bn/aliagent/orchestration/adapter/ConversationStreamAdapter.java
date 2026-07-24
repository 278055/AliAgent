package com.bn.aliagent.orchestration.adapter;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ConversationStreamPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ConversationStreamAdapter implements ConversationStreamPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedHttpClient client;
    private final Set<String> confirmed = new HashSet<>();
    private final Map<String, Integer> lastConfirmedIndex = new HashMap<>();
    public ConversationStreamAdapter(String baseUrl, String jwt, int timeoutMs, int attempts) { this.baseUrl = baseUrl; this.client = new TrustedHttpClient(jwt, timeoutMs, attempts); }
    @Override public synchronized void append(OrchestrationContract.ExecutionContext context, OrchestrationContract.StreamChunk chunk) {
        if (!context.replyMessageId().equals(chunk.messageId()) || !context.generationId().equals(chunk.generationId()) || chunk.chunkIndex() < 0) {
            throw new AdapterException(AdapterException.Category.VALIDATION, "stream chunk identifiers do not match reply generation");
        }
        String stream = context.conversationId() + ":" + context.replyMessageId() + ":" + context.generationId() + ":" + context.requestId();
        String key = stream + ":" + chunk.chunkIndex();
        if (confirmed.contains(key)) return;
        Integer last = lastConfirmedIndex.get(stream);
        if (last != null && chunk.chunkIndex() <= last) throw new AdapterException(AdapterException.Category.VALIDATION, "chunkIndex must increase monotonically");
        try {
            String body = JSON.writeValueAsString(java.util.Map.of("messageId", context.replyMessageId(), "chunkIndex", chunk.chunkIndex(), "delta", chunk.content(), "finalChunk", chunk.completed(), "finishReason", chunk.finishReason() == null ? "" : chunk.finishReason()));
            client.post(baseUrl + "/internal/api/v1/conversations/" + context.conversationId() + "/generations/" + context.generationId() + "/chunks", body, context);
            confirmed.add(key);
            lastConfirmedIndex.put(stream, chunk.chunkIndex());
        } catch (AdapterException exception) { throw exception;
        } catch (Exception exception) { throw new AdapterException(AdapterException.Category.REMOTE, "invalid conversation stream chunk", exception); }
    }
}
