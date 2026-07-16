package com.bn.aliagent.knowledge.ingestion;

import com.bn.aliagent.knowledge.catalog.EmbeddingValidator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 仅供本地集成验证使用的固定维度 Embedding 替身。 */
@Configuration
@Profile("mock")
public class MockEmbeddingConfiguration {
    @Bean
    EmbeddingModel mockEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> values = IntStream.range(0, request.getInstructions().size())
                        .mapToObj(index -> new Embedding(vector(), index)).toList();
                return new EmbeddingResponse(values);
            }

            @Override
            public float[] embed(Document document) {
                return vector();
            }

            @Override
            public int dimensions() {
                return EmbeddingValidator.DIMENSION;
            }

            private float[] vector() {
                return new float[EmbeddingValidator.DIMENSION];
            }
        };
    }
}
