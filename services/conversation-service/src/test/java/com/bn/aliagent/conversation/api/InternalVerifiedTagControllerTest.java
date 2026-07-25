package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.queue.VerifiedConversationTagRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InternalVerifiedTagControllerTest {
    @Test
    void 仅已验证服务身份可写可信标签事实() {
        UUID conversation = UUID.randomUUID();
        RecordingTags tags = new RecordingTags();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-Subject-Id", "rule-engine"); request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-Trace-Id", "trace"); request.addHeader("X-Request-Id", UUID.randomUUID().toString());
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);

        new InternalVerifiedTagController(tags).verify(conversation, new InternalVerifiedTagController.Request("AFTERSALE_VERIFIED", "AFTERSALE_VERIFIED"), request);

        assertEquals("AFTERSALE_VERIFIED", tags.tag);
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.FALSE);
        assertThrows(RuntimeException.class, () -> new InternalVerifiedTagController(tags).verify(conversation,
                new InternalVerifiedTagController.Request("AFTERSALE_VERIFIED", "AFTERSALE_VERIFIED"), request));
    }

    private static final class RecordingTags implements VerifiedConversationTagRepository {
        private String tag;
        public Set<String> verifiedTags(String tenant, UUID conversation) { return Set.of(); }
        public void verify(String tenant, UUID conversation, String tag, String source, Instant at) { this.tag = tag; }
    }
}
