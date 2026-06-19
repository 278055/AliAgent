package com.bn.aliagent.rag.pipeline;

import com.bn.aliagent.rag.model.ConversationContext;
import com.bn.aliagent.rag.model.RagChunk;
import com.bn.aliagent.rag.model.RagContext;
import com.bn.aliagent.rag.model.RetrievalContext;
import com.bn.aliagent.rag.context.ContextBuilder;
import com.bn.aliagent.rag.prompt.PromptBuilder;
import com.bn.aliagent.rag.rerank.Reranker;
import com.bn.aliagent.rag.retriever.Retriever;
import com.bn.aliagent.rag.rewrite.QueryRewriter;
import com.bn.aliagent.rag.strategy.RetrievalStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG Pipeline —— 六步调用链编排器
 *
 * <p>严格按以下顺序执行，这个编排关系永远不变，
 * 后续升级只替换各步骤的 Bean 实现，不动 Pipeline 代码：</p>
 *
 * <pre>
 * 用户提问
 *   ↓
 * ① RetrievalStrategy  —— 判断是否需要检索（false → 直接返回）
 *   ↓
 * ② QueryRewriter      —— 查询扩展/改写
 *   ↓
 * ③ Retriever          —— 向量检索（对每个改写后的查询）
 *   ↓
 * ④ Reranker           —— 重排序
 *   ↓
 * ⑤ ContextBuilder     —— 去重 + 拼接 + 截断
 *   ↓
 * ⑥ PromptBuilder      —— 组装 System Prompt + Context
 *   ↓
 * 返回 RagContext（含组装好的 Prompt）
 * </pre>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class RAGPipeline {

    private static final Logger log = LoggerFactory.getLogger(RAGPipeline.class);

    /** 上下文最大字符数 */
    private static final int MAX_CONTEXT_CHARS = 2000;

    private final RetrievalStrategy strategy;
    private final QueryRewriter rewriter;
    private final Retriever retriever;
    private final Reranker reranker;
    private final ContextBuilder contextBuilder;
    private final PromptBuilder promptBuilder;

    public RAGPipeline(RetrievalStrategy strategy,
                       QueryRewriter rewriter,
                       Retriever retriever,
                       Reranker reranker,
                       ContextBuilder contextBuilder,
                       PromptBuilder promptBuilder) {
        this.strategy = strategy;
        this.rewriter = rewriter;
        this.retriever = retriever;
        this.reranker = reranker;
        this.contextBuilder = contextBuilder;
        this.promptBuilder = promptBuilder;
    }

    /**
     * 执行完整的 RAG Pipeline
     *
     * @param userMessage    用户消息
     * @param conversationId 会话 ID
     * @return RAG 上下文（如果策略判断不需要检索，返回仅含 originalQuery 的空上下文）
     */
    public RagContext execute(String userMessage, String conversationId) {
        RagContext ctx = RagContext.builder()
                .originalQuery(userMessage)
                .build();

        // ── 步骤 ①：判断是否需要检索 ──
        ConversationContext convCtx = ConversationContext.of(conversationId);
        if (!strategy.shouldRetrieve(userMessage, convCtx)) {
            log.debug("检索策略判断：跳过 RAG，直接走普通对话");
            return ctx;
        }

        log.debug("检索策略判断：触发 RAG 检索");

        // ── 步骤 ②：查询改写 ──
        List<String> queries = rewriter.rewrite(userMessage);
        ctx.setRewrittenQueries(queries);
        log.debug("查询改写完成: {} → {} 个变体", userMessage, queries.size());

        // ── 步骤 ③：检索 ──
        RetrievalContext retrievalCtx = RetrievalContext.defaults();
        List<RagChunk> allCandidates = new ArrayList<>();
        for (String q : queries) {
            List<RagChunk> chunks = retriever.retrieve(q, retrievalCtx);
            if (chunks != null) {
                allCandidates.addAll(chunks);
            }
        }
        ctx.setCandidates(allCandidates);
        log.debug("检索完成: 共获得 {} 个候选片段", allCandidates.size());

        if (allCandidates.isEmpty()) {
            log.debug("检索结果为空，跳过后续步骤");
            return ctx;
        }

        // ── 步骤 ④：重排序 ──
        List<RagChunk> reranked = reranker.rerank(userMessage, allCandidates);
        ctx.setReranked(reranked);
        log.debug("重排序完成: {} 个片段", reranked.size());

        // ── 步骤 ⑤：上下文构建 ──
        String finalContext = contextBuilder.build(userMessage, reranked, MAX_CONTEXT_CHARS);
        ctx.setFinalContext(finalContext);
        log.debug("上下文构建完成: {} 字符（限制 {} 字符）", finalContext.length(), MAX_CONTEXT_CHARS);

        // ── 步骤 ⑥：Prompt 组装 ──
        var prompt = promptBuilder.build(ctx);
        ctx.setPrompt(prompt);
        log.debug("Prompt 组装完成: {} 条消息", prompt.getInstructions().size());

        return ctx;
    }
}
