package com.bn.aliagent.orchestration.config;

import com.bn.aliagent.orchestration.adapter.ConversationStreamAdapter;
import com.bn.aliagent.orchestration.adapter.KnowledgeServiceRetrievalAdapter;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ConversationStreamPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.KnowledgeRetrievalPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.MallReadToolPort;
import com.bn.aliagent.orchestration.core.ExecutionStore;
import com.bn.aliagent.orchestration.core.InMemoryExecutionStore;
import com.bn.aliagent.orchestration.core.JdbcExecutionStore;
import com.bn.aliagent.orchestration.core.OrchestrationService;
import com.bn.aliagent.orchestration.core.WorkflowRunner;
import com.bn.aliagent.orchestration.messaging.AiReplyRequestedV2Consumer;
import com.bn.aliagent.orchestration.messaging.AiReplyRequestedV2Mapper;
import com.bn.aliagent.orchestration.routing.Intent;
import com.bn.aliagent.orchestration.routing.RuleFirstIntentRouter;
import com.bn.aliagent.orchestration.runtime.ReadOnlyWorkflowRunner;
import com.bn.aliagent.orchestration.tool.MallReadToolAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrchestrationRuntimeConfiguration {
    @Bean
    @Profile("!database")
    ExecutionStore inMemoryExecutionStore() {
        return new InMemoryExecutionStore();
    }

    @Bean
    @Profile("database")
    ExecutionStore jdbcExecutionStore(JdbcTemplate jdbc) {
        return new JdbcExecutionStore(jdbc);
    }

    @Bean
    KnowledgeRetrievalPort knowledgeRetrievalPort(
            @Value("${orchestration.clients.knowledge-base-url:http://localhost:8083}") String baseUrl,
            @Value("${orchestration.security.service-jwt:}") String jwt,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new KnowledgeServiceRetrievalAdapter(baseUrl, jwt, timeoutMs, attempts);
    }

    @Bean
    MallReadToolPort mallReadToolPort(
            @Value("${orchestration.clients.mall-base-url:http://localhost:8080}") String baseUrl,
            @Value("${orchestration.security.service-jwt:}") String jwt,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new MallReadToolAdapter(baseUrl, jwt, timeoutMs, attempts);
    }

    @Bean
    ConversationStreamPort conversationStreamPort(
            @Value("${orchestration.clients.conversation-base-url:http://localhost:8082}") String baseUrl,
            @Value("${orchestration.security.service-jwt:}") String jwt,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new ConversationStreamAdapter(baseUrl, jwt, timeoutMs, attempts);
    }

    @Bean
    ReadOnlyWorkflowRunner readOnlyWorkflowRunner(ChatModelPort model, KnowledgeRetrievalPort knowledge,
                                                   MallReadToolPort mall, ConversationStreamPort stream) {
        return new ReadOnlyWorkflowRunner(model, knowledge, mall, stream);
    }

    @Bean
    OrchestrationService orchestrationService(ExecutionStore store, ReadOnlyWorkflowRunner runner) {
        return new OrchestrationService(store, new RuleFirstIntentRouter(input -> Intent.GENERAL), runner);
    }

    @Bean
    AiReplyRequestedV2Mapper aiReplyRequestedV2Mapper() {
        return new AiReplyRequestedV2Mapper();
    }

    @Bean
    AiReplyRequestedV2Consumer aiReplyRequestedV2Consumer(AiReplyRequestedV2Mapper mapper,
                                                            OrchestrationService service) {
        return new AiReplyRequestedV2Consumer(mapper, service);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeIncompleteExecutions(ApplicationReadyEvent ignored) {
        ignored.getApplicationContext().getBean(OrchestrationService.class).resumeIncomplete();
    }
}
