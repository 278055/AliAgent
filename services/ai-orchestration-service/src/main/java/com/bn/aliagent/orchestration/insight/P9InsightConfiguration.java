package com.bn.aliagent.orchestration.insight;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("database")
class P9InsightConfiguration {
    @Bean P9InsightOutbox p9InsightOutbox(JdbcTemplate jdbc) { return new JdbcP9InsightOutbox(jdbc); }
    @Bean P9InsightOutboxDispatcher p9InsightOutboxDispatcher(P9InsightOutbox outbox, RabbitTemplate rabbit) { return new P9InsightOutboxDispatcher(outbox, event -> rabbit.convertAndSend("insight.events.v1", event.envelope())); }
}
