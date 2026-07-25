package com.bn.aliagent.orchestration.copilot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CopilotActionTest {
    @Test
    void modifyAndSendAuditsOriginalFinalAndDiffAndIsIdempotent() {
        var repository = new CopilotRepository.InMemory();
        var sent = new int[1];
        var tenant = "test-tenant";
        var conversation = UUID.randomUUID();
        var agent = "staff-1";
        var service = new CopilotService(repository,
                (tenantId, conversationId) -> new CopilotPorts.ConversationContext(tenant, conversation, "HUMAN_ACTIVE", agent, List.of("客户消息")),
                (tenantId, conversationId) -> List.of(), (tenantId, conversationId) -> List.of(), (tenantId, conversationId) -> List.of(),
                (prompt, context) -> "原始建议", new CopilotPromptFactory(),
                (tenantId, conversationId, agentId, content, requestId) -> sent[0]++);
        var suggestion = service.generate(new CopilotModels.GenerateCommand(UUID.randomUUID(), tenant, conversation, UUID.randomUUID(), agent, agent, 0, "m1", "p1", "w1"));
        var requestId = UUID.randomUUID();

        var action = service.modifyAndSend(new CopilotModels.ActionCommand(requestId, tenant, suggestion.suggestionId(), agent, "修改后的回复"));
        service.modifyAndSend(new CopilotModels.ActionCommand(requestId, tenant, suggestion.suggestionId(), agent, "修改后的回复"));

        assertEquals("原始建议", action.originalContent());
        assertEquals("修改后的回复", action.finalContent());
        assertFalse(action.diffSummary().isBlank());
        assertEquals(1, sent[0]);
        assertFalse(action.diffSummary().contains("思维链"));
    }
}
