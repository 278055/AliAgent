package com.bn.aliagent.insight;

import com.bn.aliagent.insight.fact.DeterministicFactAnonymizer;
import com.bn.aliagent.insight.fact.InsightFactSink;
import com.bn.aliagent.insight.intake.InsightEventInbox;
import com.bn.aliagent.insight.intake.InsightEventPolicy;
import com.bn.aliagent.insight.intake.InsightIntakeService;
import com.bn.aliagent.insight.persistence.JdbcInsightEventInbox;
import com.bn.aliagent.insight.persistence.JdbcInsightFactRepository;
import com.bn.aliagent.insight.adapter.HttpMallEvidencePort;
import com.bn.aliagent.insight.adapter.MallEvidenceVerificationClient;
import com.bn.aliagent.insight.topic.ClusteringPort;
import com.bn.aliagent.insight.topic.DashScopeTopicNamingPort;
import com.bn.aliagent.insight.topic.JdbcPgVectorClusteringPort;
import com.bn.aliagent.insight.topic.TopicNamingPort;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

@Configuration
class InsightIntegrationConfiguration {
    @Bean Jackson2JsonMessageConverter insightMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
    @Bean Queue insightEventsQueue(@Value("${insight.messaging.events-queue:insight.events.v1}") String queue) { return new Queue(queue, true); }
    @Bean InsightEventPolicy insightEventPolicy() { return new InsightEventPolicy(Map.ofEntries(
            Map.entry("order.paid", Set.of(1)), Map.entry("refund.succeeded", Set.of(1)), Map.entry("logistics.exception", Set.of(1)),
            Map.entry("conversation.completed", Set.of(1)), Map.entry("conversation.feedback.received", Set.of(1)),
            Map.entry("conversation.human.requested", Set.of(1)), Map.entry("conversation.human.first-public-reply", Set.of(1)),
            Map.entry("ai.rag.completed", Set.of(1)), Map.entry("ai.citation.completed", Set.of(1)), Map.entry("ai.refused", Set.of(1)),
            Map.entry("ai.tool.failed", Set.of(1)), Map.entry("ai.query-plan.accepted", Set.of(1)),
            Map.entry("knowledge.published", Set.of(1)), Map.entry("knowledge.coverage.versioned", Set.of(1)))); }
    @Bean @Profile("database") InsightEventInbox jdbcInsightEventInbox(JdbcTemplate jdbc) { return new JdbcInsightEventInbox(jdbc); }
    @Bean @Profile("!database") InsightEventInbox inMemoryInsightEventInbox() { return new InsightEventInbox.InMemory(); }
    @Bean @Profile("database") InsightFactSink jdbcInsightFactSink(JdbcTemplate jdbc) { return new JdbcInsightFactRepository(jdbc); }
    @Bean @Profile("!database") InsightFactSink inMemoryInsightFactSink() { return fact -> { }; }
    @Bean InsightIntakeService insightIntakeService(InsightEventPolicy policy, InsightEventInbox inbox, InsightFactSink sink, @Value("${insight.anonymization.key:test-insight-anonymization-key-must-be-long}") String key) { return new InsightIntakeService(policy, inbox, new com.bn.aliagent.insight.fact.InsightFactProjector(new DeterministicFactAnonymizer(key))::project, sink, Clock.systemUTC()); }
    @Bean MallEvidenceVerificationClient mallEvidenceVerificationClient(@Value("${insight.mall.base-url:http://localhost:8080}") String baseUrl) { return new MallEvidenceVerificationClient(new HttpMallEvidencePort(baseUrl)); }
    @Bean @Profile("database") ClusteringPort pgVectorClusteringPort(JdbcTemplate jdbc) { return new JdbcPgVectorClusteringPort(jdbc); }
    @Bean TopicNamingPort dashScopeTopicNamingPort(@Value("${spring.ai.dashscope.api-key:}") String apiKey,
            @Value("${insight.dashscope.endpoint:https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation}") String endpoint) {
        return new DashScopeTopicNamingPort(apiKey, endpoint);
    }
}
