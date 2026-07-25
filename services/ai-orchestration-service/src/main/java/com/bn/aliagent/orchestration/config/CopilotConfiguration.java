package com.bn.aliagent.orchestration.config;

import com.bn.aliagent.orchestration.copilot.CopilotPorts;
import com.bn.aliagent.orchestration.copilot.CopilotPromptFactory;
import com.bn.aliagent.orchestration.copilot.CopilotRepository;
import com.bn.aliagent.orchestration.copilot.CopilotService;
import com.bn.aliagent.orchestration.copilot.CopilotSuggestionConsumer;
import com.bn.aliagent.orchestration.copilot.JdbcCopilotRepository;
import com.bn.aliagent.orchestration.copilot.TrustedConversationContextAdapter;
import com.bn.aliagent.orchestration.copilot.TrustedKnowledgeContextAdapter;
import com.bn.aliagent.orchestration.copilot.TrustedStaffMessageAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

@Configuration
@Profile("database")
public class CopilotConfiguration {
    @Bean MessageConverter p7RabbitMessageConverter() { return new Jackson2JsonMessageConverter(); }
    @Bean CopilotRepository copilotRepository(JdbcTemplate jdbc) { return new JdbcCopilotRepository(jdbc); }

    @Bean CopilotPorts.ConversationContextPort copilotConversationContextPort(
            @Value("${orchestration.clients.conversation-base-url:http://localhost:8081}") String baseUrl,
            @Value("${SERVICE_JWT_SECRET:}") String serviceSecret,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new TrustedConversationContextAdapter(baseUrl, serviceSecret, timeoutMs, attempts);
    }

    @Bean CopilotPorts.KnowledgeContextPort copilotKnowledgeContextPort(
            @Value("${orchestration.clients.knowledge-base-url:http://localhost:8083}") String baseUrl,
            @Value("${SERVICE_JWT_SECRET:}") String serviceSecret,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new TrustedKnowledgeContextAdapter(baseUrl, serviceSecret, timeoutMs, attempts);
    }

    @Bean CopilotPorts.CommerceFactPort copilotCommerceFactPort() { return (tenant, conversation) -> java.util.List.of(); }
    @Bean CopilotPorts.AfterSaleFactPort copilotAfterSaleFactPort() { return (tenant, conversation) -> java.util.List.of(); }

    @Bean CopilotPorts.StaffMessagePort copilotStaffMessagePort(
            @Value("${orchestration.clients.conversation-base-url:http://localhost:8081}") String baseUrl,
            @Value("${SERVICE_JWT_SECRET:}") String serviceSecret,
            @Value("${orchestration.timeout.connect-ms:1000}") int timeoutMs,
            @Value("${orchestration.retry.max-attempts:2}") int attempts) {
        return new TrustedStaffMessageAdapter(baseUrl, serviceSecret, timeoutMs, attempts);
    }

    @Bean CopilotPorts.CopilotModelPort copilotModelPort(
            com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort model) {
        return (prompt, context) -> model.generate(null, prompt);
    }

    @Bean CopilotService copilotService(CopilotRepository repository, CopilotPorts.ConversationContextPort conversations,
            CopilotPorts.KnowledgeContextPort knowledge, CopilotPorts.CommerceFactPort commerce,
            CopilotPorts.AfterSaleFactPort afterSale, CopilotPorts.CopilotModelPort model,
            CopilotPorts.StaffMessagePort messages) {
        return new CopilotService(repository, conversations, knowledge, commerce, afterSale, model,
                new CopilotPromptFactory(), messages);
    }

    @Bean CopilotSuggestionConsumer copilotSuggestionConsumer(CopilotService service) {
        return new CopilotSuggestionConsumer(service);
    }
}
