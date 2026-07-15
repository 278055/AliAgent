package com.macro.mall.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxEventServiceTest {
    @Test void duplicateEventIdUsesStableIdentity() throws Exception {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        OutboxEventService service = new OutboxEventService(mapper, new ObjectMapper());
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), "OrderDelivered", 1, java.time.Instant.now(), "tenant-test", "trace-test", "mall", Map.of("orderId", 1));
        service.publish(event);
        verify(mapper).insert(argThat(row -> event.getEventId().equals(row.getEventId())));
    }
    @Test void rejectsUnsupportedVersion() {
        OutboxEventService service = new OutboxEventService(mock(OutboxEventMapper.class), new ObjectMapper());
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), "x", 2, java.time.Instant.now(), "t", "tr", "mall", Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.publish(event));
    }
}
