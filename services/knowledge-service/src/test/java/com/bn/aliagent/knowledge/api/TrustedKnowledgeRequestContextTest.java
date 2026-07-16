package com.bn.aliagent.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedKnowledgeRequestContextTest {
    @Test
    void 从网关注入的完整上下文创建检索授权快照() {
        UUID snapshotId = UUID.randomUUID();
        MockHttpServletRequest request = request(snapshotId.toString());

        TrustedKnowledgeRequestContext context = TrustedKnowledgeRequestContext.from(request);

        assertEquals("tenant-a", context.tenantId());
        assertEquals("staff-a", context.subjectId());
        assertEquals(snapshotId, context.authorizationSnapshotId());
        assertEquals("KNOWLEDGE_READ", context.permissions());
    }

    @Test
    void 缺少授权快照必须拒绝而非从请求推断权限() {
        MockHttpServletRequest request = request(null);

        assertThrows(IllegalArgumentException.class, () -> TrustedKnowledgeRequestContext.from(request));
    }

    @Test
    void 上下文可绑定到服务端请求属性供检索层消费() {
        MockHttpServletRequest request = request(UUID.randomUUID().toString());
        TrustedKnowledgeRequestContext.bind(request);

        assertEquals("tenant-a", TrustedKnowledgeRequestContext.require(request).tenantId());
    }

    private MockHttpServletRequest request(String snapshotId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Subject-Id", "staff-a");
        request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-User-Roles", "KNOWLEDGE_EDITOR");
        request.addHeader("X-User-Permissions", "KNOWLEDGE_READ");
        request.addHeader("X-Trace-Id", "trace-a");
        if (snapshotId != null) request.addHeader("X-Authorization-Snapshot-Id", snapshotId);
        return request;
    }
}
