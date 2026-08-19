package com.bn.aliagent.insight.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class InsightRabbitBrokerIntegrationTest {
    @Test
    void sendsP9EnvelopeThroughRabbitBeforeInsightConsumerReceivesIt() throws Exception {
        Assumptions.assumeTrue(rabbitCredentialsConfigured(), "未设置 P9_RABBIT_USERNAME/P9_RABBIT_PASSWORD，不能连接共享 RabbitMQ 执行真实 Broker 测试");
        String queue = "test-p9-insight-" + UUID.randomUUID();
        CachingConnectionFactory connection = new CachingConnectionFactory("127.0.0.1", rabbitPort());
        connection.setUsername(rabbitUser()); connection.setPassword(rabbitPassword());
        RabbitTemplate rabbit = new RabbitTemplate(connection);
        LinkedBlockingQueue<com.rabbitmq.client.Delivery> received = new LinkedBlockingQueue<>();
        try {
            rabbit.execute(channel -> { channel.queueDeclare(queue, false, true, true, null); channel.basicConsume(queue, true, (tag, message) -> received.add(message), tag -> { }); return null; });
            UUID eventId = UUID.randomUUID();
            rabbit.convertAndSend(queue, "{\"eventId\":\"" + eventId + "\"}");
            com.rabbitmq.client.Delivery message = received.poll(5, TimeUnit.SECONDS);
            assertEquals(true, message != null);
            new InsightRabbitEventConsumer((event, tenant) -> { assertEquals(eventId, event.eventId()); assertEquals("test-tenant", tenant); return null; })
                    .consume(new InsightEventEnvelope(eventId, "conversation.completed", 1, Instant.now(), "test-tenant", "test-trace", "test-producer", Map.of("evidenceRef", "test-ref", "signal", "completed")));
        } finally {
            try { rabbit.execute(channel -> { channel.queueDelete(queue); return null; }); } finally { connection.destroy(); }
        }
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
}
