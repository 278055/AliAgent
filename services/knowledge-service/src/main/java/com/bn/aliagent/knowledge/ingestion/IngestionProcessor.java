package com.bn.aliagent.knowledge.ingestion;

import com.bn.aliagent.knowledge.catalog.EmbeddingValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class IngestionProcessor {
    public static final String CONSUMER = "knowledge-worker";
    private final IngestionTaskGateway gateway;
    private final SourceTextExtractor extractor;
    private final TextChunker chunker;
    private final TextEmbedder embedder;

    public IngestionProcessor(IngestionTaskGateway gateway, SourceTextExtractor extractor, TextChunker chunker, TextEmbedder embedder) {
        this.gateway = gateway;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embedder = embedder;
    }

    public void process(IngestionTaskMessage message) {
        if (!gateway.registerConsumption(message.eventId(), CONSUMER)) {
            return;
        }
        try {
            IngestionSource source = gateway.load(message.taskId(), message.tenantId());
            List<KnowledgeChunk> chunks = new ArrayList<>();
            int sequence = 0;
            for (String content : chunker.split(extractor.extract(source))) {
                float[] embedding = embedder.embed(content);
                EmbeddingValidator.requireDimension(embedding);
                chunks.add(new KnowledgeChunk(sequence++, content, embedding));
            }
            gateway.replaceChunks(source.versionId(), message.tenantId(), chunks);
            gateway.markReadyForReview(message.taskId(), message.tenantId());
        } catch (Exception exception) {
            gateway.markFailed(message.taskId(), message.tenantId(), diagnostic(exception));
        }
    }

    private String diagnostic(Exception exception) {
        if (exception instanceof IOException) {
            return "INGESTION_SOURCE_UNAVAILABLE";
        }
        return "INGESTION_PROCESSING_FAILED";
    }
}
