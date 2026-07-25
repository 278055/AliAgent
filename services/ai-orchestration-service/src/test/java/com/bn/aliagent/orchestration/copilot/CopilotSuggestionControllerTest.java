package com.bn.aliagent.orchestration.copilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CopilotSuggestionControllerTest {
    @Test
    void 当前接管客服可接受其私有建议且客服身份来自可信请求头() {
        String tenantId = "tenant-p7";
        String agentId = "staff-p7";
        UUID conversationId = UUID.randomUUID();
        UUID suggestionId = UUID.randomUUID();
        UUID actionRequestId = UUID.randomUUID();
        CopilotRepository.InMemory repository = new CopilotRepository.InMemory();
        repository.save(new CopilotModels.Suggestion(suggestionId, tenantId, conversationId, UUID.randomUUID(), agentId,
                0, CopilotModels.Visibility.PRIVATE, CopilotModels.SuggestionStatus.GENERATED, "建议回复", "", "",
                "mock", "p7", "p7", List.of(), Instant.now()), UUID.randomUUID());
        CopilotService service = new CopilotService(repository,
                (tenant, conversation) -> new CopilotPorts.ConversationContext(tenant, conversation, "HUMAN_ACTIVE", agentId, List.of()),
                (tenant, conversation) -> List.of(), (tenant, conversation) -> List.of(), (tenant, conversation) -> List.of(),
                (prompt, context) -> "", new CopilotPromptFactory(), (tenant, conversation, staff, content, request) -> { });
        CopilotSuggestionController controller = new CopilotSuggestionController(service, repository);

        MockHttpServletRequest request = staffRequest(tenantId, agentId);
        var response = controller.accept(conversationId, suggestionId,
                new CopilotSuggestionController.ActionRequest(actionRequestId, null), request);

        assertEquals("ACCEPTED", ((java.util.Map<?, ?>) response.get("data")).get("actionType"));
    }

    private MockHttpServletRequest staffRequest(String tenantId, String staffId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", tenantId);
        request.addHeader("X-Subject-Id", staffId);
        request.addHeader("X-Subject-Type", "STAFF");
        request.setAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE, Boolean.TRUE);
        return request;
    }
}
