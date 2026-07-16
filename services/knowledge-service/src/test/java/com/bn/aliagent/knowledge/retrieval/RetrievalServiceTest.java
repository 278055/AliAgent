package com.bn.aliagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalServiceTest {
    @Test
    void 可信上下文必须原样传递给两路受限召回() {
        RecordingKeywordRetriever keyword = new RecordingKeywordRetriever();
        RecordingSemanticRetriever semantic = new RecordingSemanticRetriever();
        TrustedKnowledgeRequestContext context = context("tenant-trusted");

        new RetrievalService(keyword, semantic, new NoOpReranker()).retrieve("退款规则", context, 3);

        assertEquals(context, keyword.context);
        assertEquals(context, semantic.context);
        assertEquals("tenant-trusted", keyword.context.tenantId());
    }

    @Test
    void 无候选时必须返回可区分的无依据结果() {
        RetrievalService service = new RetrievalService((query, context, limit) -> List.of(),
                (query, embedding, context, limit) -> List.of(), new NoOpReranker());

        RetrievalResponse response = service.retrieve("退款规则", context("tenant-a"), 3);

        assertTrue(response.items().isEmpty());
        assertEquals("没有可用的知识依据", response.message());
    }

    @Test
    void 默认Reranker保持Rrf排序且替换实现可生效() {
        RetrievalCandidate first = candidate("00000000-0000-0000-0000-000000000001");
        RetrievalCandidate second = candidate("00000000-0000-0000-0000-000000000002");
        KeywordRetriever keyword = (query, context, limit) -> List.of(first, second);
        SemanticRetriever semantic = (query, embedding, context, limit) -> List.of(first, second);
        RetrievalService noOpService = new RetrievalService(keyword, semantic, new NoOpReranker());
        RetrievalService replacementService = new RetrievalService(keyword, semantic,
                (query, candidates) -> List.of(candidates.get(1), candidates.get(0)));

        assertEquals(first.chunkId(), noOpService.retrieve("退款规则", context("tenant-a"), 2).items().get(0).chunkId());
        assertEquals(second.chunkId(), replacementService.retrieve("退款规则", context("tenant-a"), 2).items().get(0).chunkId());
    }

    @Test
    void 非1024维查询向量必须在语义召回前被拒绝() {
        RetrievalService service = new RetrievalService((query, context, limit) -> List.of(),
                (query, embedding, context, limit) -> List.of(), new NoOpReranker(), query -> new float[1023]);

        try {
            service.retrieve("退款规则", context("tenant-a"), 1);
        } catch (IllegalArgumentException exception) {
            assertEquals("嵌入向量必须为 1024 维", exception.getMessage());
            return;
        }
        throw new AssertionError("应拒绝非1024维向量");
    }

    private TrustedKnowledgeRequestContext context(String tenantId) {
        return new TrustedKnowledgeRequestContext(tenantId, "staff-a", "STAFF", "KNOWLEDGE_EDITOR", "KNOWLEDGE_READ", "trace-a", UUID.randomUUID());
    }

    private RetrievalCandidate candidate(String chunkId) {
        return new RetrievalCandidate(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                UUID.fromString("00000000-0000-0000-0000-000000000020"), UUID.fromString(chunkId), "内容", 0.9d);
    }

    private static final class RecordingKeywordRetriever implements KeywordRetriever {
        private TrustedKnowledgeRequestContext context;
        @Override public List<RetrievalCandidate> retrieve(String query, TrustedKnowledgeRequestContext requestContext, int limit) {
            context = requestContext;
            return new ArrayList<>();
        }
    }

    private static final class RecordingSemanticRetriever implements SemanticRetriever {
        private TrustedKnowledgeRequestContext context;
        @Override public List<RetrievalCandidate> retrieve(String query, float[] embedding, TrustedKnowledgeRequestContext requestContext, int limit) {
            context = requestContext;
            return new ArrayList<>();
        }
    }
}
