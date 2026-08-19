package com.bn.aliagent.knowledge.insight;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
class KnowledgeInsightInfrastructureConfiguration {
    @Bean KnowledgeInsightOutboxDispatcher knowledgeInsightOutboxDispatcher(KnowledgeInsightOutbox outbox, RabbitTemplate rabbit) {
        return new KnowledgeInsightOutboxDispatcher(outbox, event -> rabbit.convertAndSend("insight.events.v1", event.envelope()));
    }
}
