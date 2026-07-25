package com.bn.aliagent.orchestration.copilot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CopilotServiceTest {
    private static final String TENANT = "test-tenant";
    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final UUID MESSAGE = UUID.randomUUID();
    private static final String AGENT = "staff-1";

    @Test
    void onlyHumanActiveAssignedAgentCanGeneratePrivateSuggestion() {
        var repository = new CopilotRepository.InMemory();
        var service = service(repository, "HUMAN_ACTIVE", AGENT, false);
        var command = command(0, AGENT);

        var suggestion = service.generate(command);

        assertEquals(CopilotModels.SuggestionStatus.GENERATED, suggestion.status());
        assertEquals(CopilotModels.Visibility.PRIVATE, suggestion.visibility());
        assertEquals("建议：请核对订单状态后向客户说明。", suggestion.originalContent());
        assertThrows(CopilotException.class, () -> service.generate(command(1, "staff-2")));
    }

    @Test
    void dependencyFailureCreatesRetryableSuggestionWithoutInventingFacts() {
        var repository = new CopilotRepository.InMemory();
        var service = service(repository, "HUMAN_ACTIVE", AGENT, true);

        var suggestion = service.generate(command(0, AGENT));

        assertEquals(CopilotModels.SuggestionStatus.FAILED_RETRYABLE, suggestion.status());
        assertTrue(suggestion.originalContent().isBlank());
        assertEquals(0, service.modelCalls());
    }

    @Test
    void duplicateEventIsIgnoredByInbox() {
        var repository = new CopilotRepository.InMemory();
        var service = service(repository, "HUMAN_ACTIVE", AGENT, false);
        var consumer = new CopilotSuggestionConsumer(service);
        var event = new CopilotModels.SuggestionRequested(UUID.randomUUID(), command(0, AGENT));

        consumer.accept(event);
        consumer.accept(event);

        assertEquals(1, service.modelCalls());
    }

    private CopilotService service(CopilotRepository repository, String status, String assignedAgent, boolean unavailable) {
        var context = new CopilotPorts.ConversationContext(TENANT, CONVERSATION, status, assignedAgent, List.of("客户：订单何时发货？"));
        var conversations = new CopilotPorts.ConversationContextPort() {
            @Override public CopilotPorts.ConversationContext load(String tenantId, UUID conversationId) { return context; }
        };
        var knowledge = new CopilotPorts.KnowledgeContextPort() {
            @Override public List<CopilotModels.Citation> retrieve(String tenantId, UUID conversationId) {
                if (unavailable) throw new CopilotException("知识服务不可用");
                return List.of(new CopilotModels.Citation("发货时效", "kb://shipping"));
            }
        };
        var commerce = (CopilotPorts.CommerceFactPort) (tenantId, conversationId) -> List.of("订单已支付");
        var afterSale = (CopilotPorts.AfterSaleFactPort) (tenantId, conversationId) -> List.of("暂无售后申请");
        return new CopilotService(repository, conversations, knowledge, commerce, afterSale,
                (prompt, context1) -> "建议：请核对订单状态后向客户说明。", new CopilotPromptFactory(), new CopilotPorts.StaffMessagePort() {
                    @Override public void send(String tenantId, UUID conversationId, String agentId, String content, UUID requestId) { }
                });
    }

    private CopilotModels.GenerateCommand command(int refreshNo, String agent) {
        return new CopilotModels.GenerateCommand(UUID.randomUUID(), TENANT, CONVERSATION, MESSAGE, AGENT, agent, refreshNo,
                "model-v1", "prompt-v1", "workflow-v1");
    }
}
