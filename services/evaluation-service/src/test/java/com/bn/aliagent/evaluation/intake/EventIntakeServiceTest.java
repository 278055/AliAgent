package com.bn.aliagent.evaluation.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventIntakeServiceTest {
    private static final String TENANT = "test-p8-a-tenant";

    @Test
    void duplicateEventCreatesOnlyOneCandidate() {
        var inbox = new EventInbox.InMemory();
        var sink = new RecordingSink();
        var service = new EventIntakeService(inbox, safeAnonymizer(), sink, Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));
        var event = event(UUID.randomUUID(), "conversation.feedback.received", 1, TENANT);

        assertEquals(IntakeStatus.COMPLETED, service.accept(event, TENANT).status());
        assertEquals(IntakeStatus.COMPLETED, service.accept(event, TENANT).status());

        assertEquals(1, sink.accepted);
        assertEquals(IntakeStatus.COMPLETED, inbox.require(event.eventId()).status());
        assertEquals(false, inbox.require(event.eventId()).contentDigest().contains("13800138000"));
    }

    @Test
    void rejectsUnknownVersionAndUntrustedTenant() {
        var service = new EventIntakeService(new EventInbox.InMemory(), safeAnonymizer(), new RecordingSink(), Clock.systemUTC());

        assertThrows(IllegalArgumentException.class, () -> service.accept(event(UUID.randomUUID(), "conversation.feedback.received", 2, TENANT), TENANT));
        assertThrows(SecurityException.class, () -> service.accept(event(UUID.randomUUID(), "conversation.feedback.received", 1, "client-tenant"), TENANT));
    }

    @Test
    void failedEventCanRetryWithoutCreatingSecondCandidate() {
        var inbox = new EventInbox.InMemory();
        var sink = new RecordingSink();
        var attempts = new int[1];
        PayloadAnonymizer flaky = (payload, tenantId) -> {
            if (attempts[0]++ == 0) throw new IllegalStateException("sanitizer unavailable");
            return new AnonymizedPayload(IntakeAnonymizationStatus.SAFE, "{\"message\":\"ok\"}", "anon-v1", "digest");
        };
        var service = new EventIntakeService(inbox, flaky, sink, Clock.systemUTC());
        var event = event(UUID.randomUUID(), "conversation.feedback.received", 1, TENANT);

        assertThrows(IllegalStateException.class, () -> service.accept(event, TENANT));
        assertEquals(IntakeStatus.COMPLETED, service.accept(event, TENANT).status());
        assertEquals(1, sink.accepted);
    }

    private static PayloadAnonymizer safeAnonymizer() {
        return (payload, tenantId) -> new AnonymizedPayload(IntakeAnonymizationStatus.SAFE, "{\"message\":\"ok\"}", "anon-v1", "digest");
    }

    private static EvaluationEventEnvelope event(UUID eventId, String type, int version, String tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", "电话 13800138000");
        return new EvaluationEventEnvelope(eventId, type, version, Instant.parse("2026-07-28T00:00:00Z"), tenantId, "trace", "test-producer", payload);
    }

    private static final class RecordingSink implements CandidateSink {
        private int accepted;
        @Override public void accept(IntakeCandidate candidate) { accepted++; }
    }
}
