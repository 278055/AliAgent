package com.bn.aliagent.knowledge.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bn.aliagent.insight.adapter.InsightRabbitEventConsumer;
import com.bn.aliagent.insight.fact.DeterministicFactAnonymizer;
import com.bn.aliagent.insight.fact.InsightFactProjector;
import com.bn.aliagent.insight.intake.InsightEventInbox;
import com.bn.aliagent.insight.intake.InsightEventPolicy;
import com.bn.aliagent.insight.intake.InsightIntakeService;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

class KnowledgeInsightRabbitEndToEndTest {
    @Test
    void dispatchesKnowledgeOutboxThroughRabbitAndWritesInsightFact() throws Exception {
        Assumptions.assumeTrue(rabbitMqAvailable(), "RabbitMQ 127.0.0.1:" + rabbitPort() + " 不可达，不能伪造真实 MQ E2E 结果");
        Assumptions.assumeTrue(rabbitCredentialsConfigured(), "未设置 P9_RABBIT_USERNAME/P9_RABBIT_PASSWORD，不能连接共享 RabbitMQ 执行真实 E2E");
        String queue = "test-p9-insight-e2e-" + UUID.randomUUID();
        CachingConnectionFactory connection = new CachingConnectionFactory("127.0.0.1", rabbitPort());
        connection.setUsername(rabbitUser());
        connection.setPassword(rabbitPassword());
        RabbitTemplate rabbit = new RabbitTemplate(connection);
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        rabbit.setMessageConverter(converter);
        List<com.bn.aliagent.insight.fact.AnonymizedFact> facts = new ArrayList<>();
        CountDownLatch received = new CountDownLatch(1);
        InsightEventInbox inbox = new InsightEventInbox.InMemory();
        InsightIntakeService intake = new InsightIntakeService(policy(), inbox,
                new InsightFactProjector(new DeterministicFactAnonymizer("test-p9-e2e-anonymization-key"))::project,
                fact -> { facts.add(fact); received.countDown(); }, Clock.systemUTC());
        InsightRabbitEventConsumer consumer = new InsightRabbitEventConsumer(intake);
        SimpleMessageListenerContainer listener = new SimpleMessageListenerContainer(connection);
        listener.setQueueNames(queue);
        MessageListenerAdapter adapter = new MessageListenerAdapter(consumer, "consume");
        adapter.setMessageConverter(converter);
        listener.setMessageListener(adapter);
        TestOutbox outbox = new TestOutbox(new KnowledgeInsightEvent(UUID.randomUUID(), "knowledge.published", "test-p9-tenant",
                "test-p9-trace", Instant.now(), "test-p9-evidence", "test-p9-coverage-v1"));
        try {
            rabbit.execute(channel -> { channel.queueDeclare(queue, false, true, true, null); return null; });
            listener.start();
            new KnowledgeInsightOutboxDispatcher(outbox, event -> rabbit.convertAndSend(queue, event.envelope())).dispatchPending();
            assertTrue(received.await(10, TimeUnit.SECONDS));
            assertEquals(List.of(outbox.event.eventId()), outbox.published);
            assertEquals(1, facts.size());
            assertEquals("test-p9-tenant", facts.get(0).tenantId());
            assertEquals("knowledge.published", facts.get(0).type());
            assertEquals(com.bn.aliagent.insight.intake.IntakeStatus.COMPLETED,
                    inbox.require("test-p9-tenant", "knowledge-service", outbox.event.eventId()).status());
        } finally {
            try { listener.stop(); rabbit.execute(channel -> { channel.queueDelete(queue); return null; }); }
            finally { connection.destroy(); }
        }
    }

    private static InsightEventPolicy policy() {
        return new InsightEventPolicy(Map.of("knowledge.published", java.util.Set.of(1)));
    }

    private static boolean rabbitMqAvailable() {
        try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", rabbitPort()), 500); return true; }
        catch (Exception ignored) { return false; }
    }

    private static int rabbitPort() {
        String configured = System.getenv("P9_RABBIT_PORT");
        return configured == null || configured.isBlank() ? 15692 : Integer.parseInt(configured);
    }

    private static String rabbitUser() { return System.getenv("P9_RABBIT_USERNAME"); }
    private static String rabbitPassword() { return System.getenv("P9_RABBIT_PASSWORD"); }
    private static boolean rabbitCredentialsConfigured() {
        return rabbitUser() != null && !rabbitUser().isBlank() && rabbitPassword() != null && !rabbitPassword().isBlank();
    }

    private static final class TestOutbox implements KnowledgeInsightOutbox {
        private final KnowledgeInsightEvent event;
        private final List<UUID> published = new ArrayList<>();
        private TestOutbox(KnowledgeInsightEvent event) { this.event = event; }
        @Override public void append(String tenantId, String traceId, UUID versionId, String coverageVersion) { }
        @Override public List<KnowledgeInsightEvent> pending(int limit) { return List.of(event); }
        @Override public void markPublished(UUID eventId) { published.add(eventId); }
    }
}
