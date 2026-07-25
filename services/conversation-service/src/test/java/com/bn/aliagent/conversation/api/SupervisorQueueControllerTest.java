package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bn.aliagent.conversation.assignment.AssignmentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SupervisorQueueControllerTest {
    @Test
    void 带SUPERVISOR角色的客服可查看主管队列() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);
        request.addHeader("X-Tenant-Id", "tenant-a"); request.addHeader("X-Subject-Id", "staff-a"); request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-User-Roles", "STAFF,SUPERVISOR"); request.addHeader("X-Trace-Id", "trace-a"); request.addHeader("X-Request-Id", UUID.randomUUID().toString());

        assertDoesNotThrow(() -> new SupervisorQueueController(mock(AssignmentService.class)).queue(request));
    }
    @Test
    void supervisorCanCreateOfferForWaitingQueueItem() {
        AssignmentService assignments = mock(AssignmentService.class);
        UUID queueItemId = UUID.randomUUID();
        when(assignments.offer(org.mockito.ArgumentMatchers.eq(queueItemId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.bn.aliagent.conversation.assignment.AssignmentModels.Offer(UUID.randomUUID(), "tenant-a", queueItemId,
                        UUID.randomUUID(), "staff-a", java.time.Instant.now().plusSeconds(30),
                        com.bn.aliagent.conversation.assignment.AssignmentModels.OfferStatus.PENDING, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);
        request.addHeader("X-Tenant-Id", "tenant-a"); request.addHeader("X-Subject-Id", "staff-a"); request.addHeader("X-Subject-Type", "STAFF");
        request.addHeader("X-User-Roles", "STAFF,SUPERVISOR"); request.addHeader("X-Trace-Id", "trace-a"); request.addHeader("X-Request-Id", UUID.randomUUID().toString());

        new SupervisorQueueController(assignments).offer(queueItemId, request);

        verify(assignments).offer(org.mockito.ArgumentMatchers.eq(queueItemId), org.mockito.ArgumentMatchers.any());
    }
}
