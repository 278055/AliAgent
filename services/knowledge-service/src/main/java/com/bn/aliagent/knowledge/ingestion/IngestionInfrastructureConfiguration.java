package com.bn.aliagent.knowledge.ingestion;

import com.bn.aliagent.knowledge.storage.KnowledgeObjectStorage;
import java.io.InputStream;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class IngestionInfrastructureConfiguration {
    public static final String QUEUE = "knowledge.ingestion.v1";

    @Bean
    Queue knowledgeIngestionQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    Jackson2JsonMessageConverter knowledgeMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    IngestionProcessor ingestionProcessor(IngestionTaskGateway gateway, KnowledgeObjectStorage storage, EmbeddingModel embeddingModel) {
        Tika tika = new Tika();
        TokenTextSplitter splitter = new TokenTextSplitter();
        return new IngestionProcessor(gateway, source -> {
            try (InputStream input = storage.get(source.objectKey())) {
                return tika.parseToString(input);
            }
        }, text -> splitter.split(new Document(text)).stream().map(Document::getText).toList(), embeddingModel::embed);
    }
}
