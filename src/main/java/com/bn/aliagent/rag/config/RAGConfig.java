package com.bn.aliagent.rag.config;

import com.bn.aliagent.rag.context.ContextBuilder;
import com.bn.aliagent.rag.context.SimpleContextBuilder;
import com.bn.aliagent.rag.pipeline.RAGPipeline;
import com.bn.aliagent.rag.prompt.BasicPromptBuilder;
import com.bn.aliagent.rag.prompt.PromptBuilder;
import com.bn.aliagent.rag.rerank.NoOpReranker;
import com.bn.aliagent.rag.rerank.Reranker;
import com.bn.aliagent.rag.retriever.Retriever;
import com.bn.aliagent.rag.retriever.HttpRemoteKnowledgeClient;
import com.bn.aliagent.rag.retriever.RemoteKnowledgeClient;
import com.bn.aliagent.rag.retriever.RemoteKnowledgeReadProperties;
import com.bn.aliagent.rag.retriever.RemoteReadRetriever;
import com.bn.aliagent.rag.retriever.Slf4jRemoteReadObservation;
import com.bn.aliagent.rag.retriever.TrustedKnowledgeContextResolver;
import com.bn.aliagent.rag.retriever.VectorRetriever;
import com.bn.aliagent.rag.rewrite.NoOpQueryRewriter;
import com.bn.aliagent.rag.rewrite.QueryRewriter;
import com.bn.aliagent.rag.strategy.RetrievalStrategy;
import com.bn.aliagent.rag.strategy.RuleStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 模块 Spring 装配配置
 *
 * <p>所有接口 → 实现类的 Bean 映射集中管理。</p>
 *
 * <p><b>二期升级只需替换此处的 Bean 实现，不动任何其他代码：</b></p>
 * <pre>
 * // 示例：升级检索器
 * // @Bean public Retriever retriever(VectorStore vs) { return new HybridRetriever(vs); }
 *
 * // 示例：升级重排序
 * // @Bean public Reranker reranker() { return new DashScopeReranker(); }
 * </pre>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Configuration
public class RAGConfig {

    // ──────────── 步骤 ①：检索策略 ────────────

    @Bean
    public RetrievalStrategy retrievalStrategy() {
        return new RuleStrategy();
    }

    // ──────────── 步骤 ②：查询改写 ────────────

    @Bean
    public QueryRewriter queryRewriter() {
        return new NoOpQueryRewriter();
    }

    // ──────────── 步骤 ③：检索器 ────────────

    @Bean
    public Retriever retriever(VectorStore vectorStore,
            @Value("${feature.knowledge.remote-read:false}") boolean remoteReadEnabled,
            @Value("${feature.knowledge.remote-read-tenants:}") String remoteReadTenants,
            @Value("${feature.knowledge.remote-read-dual-run:false}") boolean dualRun,
            @Value("${knowledge-service.base-url:http://localhost:8084}") String knowledgeServiceBaseUrl,
            @Value("${knowledge-service.remote-read-timeout:2s}") Duration timeout,
            ObjectMapper objectMapper) {
        RemoteKnowledgeReadProperties properties = new RemoteKnowledgeReadProperties(remoteReadEnabled,
                Arrays.stream(remoteReadTenants.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList(),
                dualRun, knowledgeServiceBaseUrl, timeout);
        RemoteKnowledgeClient remoteClient = new HttpRemoteKnowledgeClient(HttpClient.newHttpClient(), objectMapper,
                properties.baseUrl(), properties.timeout());
        return new RemoteReadRetriever(new VectorRetriever(vectorStore), properties, remoteClient,
                new TrustedKnowledgeContextResolver(), new Slf4jRemoteReadObservation(), ForkJoinPool.commonPool());
    }

    // ──────────── 步骤 ④：重排序 ────────────

    @Bean
    public Reranker reranker() {
        return new NoOpReranker();
    }

    // ──────────── 步骤 ⑤：上下文构建 ────────────

    @Bean
    public ContextBuilder contextBuilder() {
        return new SimpleContextBuilder();
    }

    // ──────────── 步骤 ⑥：Prompt 组装 ────────────

    @Bean
    public PromptBuilder promptBuilder() {
        return new BasicPromptBuilder();
    }

    // ──────────── Pipeline 编排 ────────────

    @Bean
    public RAGPipeline ragPipeline(RetrievalStrategy strategy,
                                    QueryRewriter rewriter,
                                    Retriever retriever,
                                    Reranker reranker,
                                    ContextBuilder contextBuilder,
                                    PromptBuilder promptBuilder) {
        return new RAGPipeline(strategy, rewriter, retriever, reranker, contextBuilder, promptBuilder);
    }
}
