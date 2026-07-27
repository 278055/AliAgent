package com.bn.aliagent.evaluation.dataset;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DatasetControllerAuthorizationTest {
    @Test
    void rejectsUnverifiedOrUnprivilegedHeadersBeforeBodyCanAuthorizePublication() {
        DatasetController controller = new DatasetController(null);
        MockHttpServletRequest forged = request(false, "EVALUATION_DATASET_PUBLIC_PUBLISH");
        assertThrows(SecurityException.class, () -> controller.publish(UUID.randomUUID(), true, forged));
        MockHttpServletRequest ordinary = request(true, "");
        assertThrows(SecurityException.class, () -> controller.publish(UUID.randomUUID(), true, ordinary));
    }
    private static MockHttpServletRequest request(boolean verified, String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "tenant"); request.addHeader("X-Subject-Id", "subject"); request.addHeader("X-User-Permissions", permissions);
        if (verified) request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);
        return request;
    }
}
