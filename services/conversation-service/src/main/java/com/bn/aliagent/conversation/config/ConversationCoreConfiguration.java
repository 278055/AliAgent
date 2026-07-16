package com.bn.aliagent.conversation.config;

import com.bn.aliagent.conversation.core.AIReplyRequestedPublisher;
import com.bn.aliagent.conversation.core.ConversationOutboxDispatcher;
import com.bn.aliagent.conversation.core.ConversationRepository;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database")
public class ConversationCoreConfiguration {
    @Bean
    Queue aiReplyRequestedQueue() { return new Queue("ai.reply.requested.v1", true); }

    @Bean
    AIReplyRequestedPublisher aiReplyRequestedPublisher(RabbitTemplate rabbit) {
        return request -> rabbit.convertAndSend("ai.reply.requested.v1", request);
    }

    @Bean
    ConversationOutboxDispatcher conversationOutboxDispatcher(ConversationRepository repository, AIReplyRequestedPublisher publisher) {
        return new ConversationOutboxDispatcher(repository, publisher);
    }
}
