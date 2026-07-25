package com.bn.aliagent.knowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TrustedKnowledgeContextFilterTest {
    @Test
    void 内部快照签发在服务认证完成后不要求已有快照() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/authorization-snapshots");
        request.setAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TrustedKnowledgeContextFilter().doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> ((MockHttpServletResponse) ignoredResponse).setStatus(204));

        assertEquals(204, response.getStatus());
    }
}
