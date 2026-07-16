package com.bn.aliagent.knowledge.retrieval;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
class RetrievalConfiguration {
    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    Reranker reranker() {
        return new NoOpReranker();
    }

    @Bean
    RetrievalService retrievalService(KeywordRetriever keywordRetriever, SemanticRetriever semanticRetriever, Reranker reranker,
            EmbeddingModel embeddingModel) {
        return new RetrievalService(keywordRetriever, semanticRetriever, reranker, embeddingModel::embed);
    }
}
