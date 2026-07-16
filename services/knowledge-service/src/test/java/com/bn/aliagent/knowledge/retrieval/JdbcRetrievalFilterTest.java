package com.bn.aliagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcRetrievalFilterTest {
    @Test
    void 两路查询都必须在Sql层限制已发布版本租户知识域与授权快照() {
        String filter = JdbcKeywordRetriever.FILTER;

        assertTrue(filter.contains("chunk.tenant_id = ?"));
        assertTrue(filter.contains("version.state = 'PUBLISHED'"));
        assertTrue(filter.contains("EXISTS (SELECT 1"));
        assertTrue(filter.contains("knowledge_document_domain"));
        assertTrue(filter.contains("snapshot.id = ?"));
        assertTrue(filter.contains("snapshot.subject_id = ?"));
        assertTrue(filter.contains("snapshot.expires_at > CURRENT_TIMESTAMP"));
        assertTrue(filter.contains("knowledge_authorization_snapshot_domain"));
        assertTrue(filter.contains("snapshot_domain.permission = 'KNOWLEDGE_READ'"));
    }

    @Test
    void 伪造租户值不能覆盖可信上下文的Sql参数() {
        TrustedKnowledgeRequestContext context = new TrustedKnowledgeRequestContext("tenant-trusted", "staff-a", "STAFF",
                "KNOWLEDGE_EDITOR", "KNOWLEDGE_READ", "trace-a", UUID.randomUUID());

        Object[] parameters = JdbcKeywordRetriever.parameters(context, "退款", "退款", 10);

        assertEquals("退款", parameters[0]);
        assertEquals("tenant-trusted", parameters[1]);
        assertEquals(context.authorizationSnapshotId(), parameters[2]);
        assertEquals("staff-a", parameters[3]);
        assertEquals("退款", parameters[7]);
        assertEquals(10, parameters[8]);
    }

    @Test
    void 关键词与向量查询分别使用全文和余弦运算符() {
        String keywordSql = "websearch_to_tsquery('simple', ?) " + JdbcKeywordRetriever.FILTER
                + "AND chunk.content_tsv @@ websearch_to_tsquery('simple', ?)";
        String semanticSql = "1 - (chunk.embedding <=> ?) " + JdbcKeywordRetriever.FILTER
                + "ORDER BY chunk.embedding <=> ?";

        assertTrue(keywordSql.contains("content_tsv @@ websearch_to_tsquery"));
        assertTrue(semanticSql.contains("chunk.embedding <=> ?"));
    }

    @Test
    void 草稿和退休版本均不满足唯一的已发布可见性条件() {
        assertTrue(JdbcKeywordRetriever.FILTER.contains("version.state = 'PUBLISHED'"));
        assertTrue(!JdbcKeywordRetriever.FILTER.contains("'DRAFT'"));
        assertTrue(!JdbcKeywordRetriever.FILTER.contains("'RETIRED'"));
    }
}
