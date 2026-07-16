package com.bn.aliagent.knowledge.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.knowledge.api.TrustedKnowledgeRequestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RetrievalControllerTest {
    @Test
    void 响应应包含切片来源与引用元数据且只使用绑定的可信租户() {
        RetrievalCandidate candidate = new RetrievalCandidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "退款规则", 0.5d);
        RetrievalService service = new RetrievalService((query, context, limit) -> List.of(candidate),
                (query, embedding, context, limit) -> List.of(), new NoOpReranker());
        RetrievalController controller = new RetrievalController(service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        TrustedKnowledgeRequestContext.bind(request(request, "tenant-trusted"));

        Map<String, Object> response = controller.retrieve(request, new RetrievalController.RetrievalQuery("退款", null));

        assertEquals(200, response.get("code"));
        Map<?, ?> data = (Map<?, ?>) response.get("data");
        Map<?, ?> item = (Map<?, ?>) ((List<?>) data.get("items")).get(0);
        assertEquals(candidate.documentId(), item.get("documentId"));
        assertEquals(candidate.chunkId(), ((Map<?, ?>) item.get("citation")).get("chunkId"));
    }

    private MockHttpServletRequest request(MockHttpServletRequest request, String tenantId) {
        request.addHeader("X-Tenant-Id", tenantId);
        request.addHeader("X-Subject-Id", "staff-a");
        request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-User-Roles", "KNOWLEDGE_EDITOR");
        request.addHeader("X-User-Permissions", "KNOWLEDGE_READ");
        request.addHeader("X-Trace-Id", "trace-a");
        request.addHeader("X-Authorization-Snapshot-Id", UUID.randomUUID().toString());
        return request;
    }
}
