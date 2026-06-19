package com.bn.aliagent.rag.config;

import com.bn.aliagent.rag.context.ContextBuilder;
import com.bn.aliagent.rag.context.SimpleContextBuilder;
import com.bn.aliagent.rag.pipeline.RAGPipeline;
import com.bn.aliagent.rag.prompt.BasicPromptBuilder;
import com.bn.aliagent.rag.prompt.PromptBuilder;
import com.bn.aliagent.rag.rerank.NoOpReranker;
import com.bn.aliagent.rag.rerank.Reranker;
import com.bn.aliagent.rag.retriever.Retriever;
import com.bn.aliagent.rag.retriever.VectorRetriever;
import com.bn.aliagent.rag.rewrite.NoOpQueryRewriter;
import com.bn.aliagent.rag.rewrite.QueryRewriter;
import com.bn.aliagent.rag.strategy.RetrievalStrategy;
import com.bn.aliagent.rag.strategy.RuleStrategy;
import org.springframework.ai.vectorstore.VectorStore;
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
    public Retriever retriever(VectorStore vectorStore) {
        return new VectorRetriever(vectorStore);
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
