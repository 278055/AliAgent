package com.bn.aliagent.conversation.api;

import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalCopilotContextControllerTest {
    @Test
    void trustedCopilotContextLoadsMemberMessagesWithoutPublicVisibilityFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID conversationId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(conversationId), eq("tenant-p7")))
                .thenReturn(List.of(Map.of("tenant_id", "tenant-p7", "collaboration_status", "HUMAN_ACTIVE", "staff_id", "staff-p7")));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("tenant-p7"), eq(conversationId)))
                .thenReturn(List.of("会员私有消息"));
        when(jdbc.queryForList(anyString(), eq("tenant-p7"), eq(conversationId))).thenReturn(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE, Boolean.TRUE);
        request.addHeader("X-Tenant-Id", "tenant-p7"); request.addHeader("X-Subject-Id", "copilot");
        request.addHeader("X-Subject-Type", "SERVICE"); request.addHeader("X-Trace-Id", "trace-p7"); request.addHeader("X-Request-Id", UUID.randomUUID().toString());

        new InternalCopilotContextController(jdbc).context(conversationId, request);

        verify(jdbc).query(org.mockito.ArgumentMatchers.argThat(sql -> !sql.contains("visibility='PUBLIC'")),
                any(org.springframework.jdbc.core.RowMapper.class), eq("tenant-p7"), eq(conversationId));
    }
}
