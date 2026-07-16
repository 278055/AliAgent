package com.bn.aliagent.legacy;

import com.bn.aliagent.rag.retriever.HttpRemoteKnowledgeClient;
import com.bn.aliagent.rag.retriever.RemoteKnowledgeClient;
import com.bn.aliagent.rag.retriever.RemoteKnowledgeReadProperties;
import com.bn.aliagent.rag.retriever.RemoteReadRetriever;
import com.bn.aliagent.rag.retriever.Retriever;
import com.bn.aliagent.rag.retriever.Slf4jRemoteReadObservation;
import com.bn.aliagent.rag.retriever.TrustedKnowledgeContextResolver;
import com.bn.aliagent.rag.retriever.VectorRetriever;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LegacyRagProbeConfiguration {
    @Bean
    Retriever retriever(@Value("${feature.knowledge.remote-read:false}") boolean enabled,
            @Value("${feature.knowledge.remote-read-tenants:}") String tenants,
            @Value("${feature.knowledge.remote-read-dual-run:false}") boolean dualRun,
            @Value("${knowledge-service.base-url:http://localhost:8084}") String baseUrl,
            @Value("${knowledge-service.remote-read-timeout:2s}") Duration timeout, ObjectMapper mapper) {
        RemoteKnowledgeReadProperties properties = new RemoteKnowledgeReadProperties(enabled,
                java.util.Arrays.stream(tenants.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList(),
                dualRun, baseUrl, timeout);
        RemoteKnowledgeClient remote = new HttpRemoteKnowledgeClient(HttpClient.newHttpClient(), mapper, baseUrl, timeout);
        return new RemoteReadRetriever(new VectorRetriever(localVectorStore()), properties, remote,
                new TrustedKnowledgeContextResolver(), new Slf4jRemoteReadObservation(), Runnable::run);
    }

    private VectorStore localVectorStore() {
        return new VectorStore() {
            @Override public void add(List<Document> documents) { }
            @Override public void delete(List<String> ids) { }
            @Override public void delete(Filter.Expression expression) { }
            @Override public List<Document> similaritySearch(SearchRequest request) {
                return List.of(new Document("local fallback result", Map.of("document_id", "local-document")));
            }
        };
    }
}
