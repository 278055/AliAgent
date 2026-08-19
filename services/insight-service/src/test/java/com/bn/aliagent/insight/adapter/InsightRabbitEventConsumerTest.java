package com.bn.aliagent.insight.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.insight.intake.InsightEventEnvelope;
import com.bn.aliagent.insight.intake.InsightIntakeRecord;
import com.bn.aliagent.insight.intake.InsightIntakeService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightRabbitEventConsumerTest {
    @Test
    void forwardsTrustedV1EnvelopeToIntake() {
        var eventId = UUID.randomUUID();
        var consumer = new InsightRabbitEventConsumer((event, tenant) -> {
            assertEquals(eventId, event.eventId());
            assertEquals("test-tenant", tenant);
            return null;
        });

        consumer.consume(new InsightEventEnvelope(eventId, "order.paid", 1, Instant.now(), "test-tenant", "test-trace", "mall", Map.of("orderRef", "test-order")));
    }

    @Test
    void rejectsEnvelopeWithoutTenantBeforeIntake() {
        var consumer = new InsightRabbitEventConsumer((event, tenant) -> {
            throw new AssertionError("不应调用 Intake");
        });

        assertThrows(SecurityException.class, () -> consumer.consume(new InsightEventEnvelope(UUID.randomUUID(), "order.paid", 1, Instant.now(), "", "test-trace", "mall", Map.of())));
    }

    @Test
    void rebuildsJsonOutboxEnvelopeWithoutTrustingTypeHeaders() {
        var eventId = UUID.randomUUID();
        var consumer = new InsightRabbitEventConsumer((event, tenant) -> {
            assertEquals(eventId, event.eventId());
            assertEquals("test-tenant", tenant);
            assertEquals("knowledge-service", event.producer());
            return null;
        });
        consumer.consume(Map.of("eventId", eventId.toString(), "eventType", "knowledge.published", "eventVersion", 1,
                "occurredAt", Instant.now().toString(), "tenantId", "test-tenant", "traceId", "test-trace",
                "producer", "knowledge-service", "payload", Map.of("evidenceRef", "test-ref")));
    }

}
