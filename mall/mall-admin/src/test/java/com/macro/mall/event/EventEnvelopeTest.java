package com.macro.mall.event;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeTest {
    @Test void containsAllContractFields() {
        EventEnvelope e = new EventEnvelope(UUID.randomUUID(), "ProductChanged", 1, Instant.now(), "tenant-test", "trace-test", "mall", Map.of("id", 1));
        assertNotNull(e.getEventId()); assertEquals(1, e.getEventVersion()); assertEquals("mall", e.getProducer());
        assertNotNull(e.getOccurredAt()); assertNotNull(e.getTenantId()); assertNotNull(e.getTraceId()); assertNotNull(e.getPayload());
    }
}
