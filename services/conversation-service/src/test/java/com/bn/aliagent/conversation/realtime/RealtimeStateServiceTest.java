package com.bn.aliagent.conversation.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeStateServiceTest {
    @Test
    void storesMinimalAgentPresenceAndHumanState() {
        RecordingStateRepository repository = new RecordingStateRepository();
        RealtimeStateService service = new RealtimeStateService(repository);
        UUID conversationId = UUID.randomUUID();

        service.updatePresence("test-p4-c-tenant", "staff-1", "ONLINE");
        service.updateHumanState("test-p4-c-tenant", conversationId, "staff-1", "HUMAN_ACTIVE");

        assertEquals("ONLINE", repository.presences.get(0));
        assertEquals("HUMAN_ACTIVE", repository.humanStates.get(0));
    }

    @Test
    void rejectsPresenceOutsideMinimalContract() {
        RealtimeStateService service = new RealtimeStateService(new RecordingStateRepository());
        assertThrows(IllegalArgumentException.class,
                () -> service.updatePresence("test-p4-c-tenant", "staff-1", "AWAY"));
    }

    private static final class RecordingStateRepository implements RealtimeStateRepository {
        private final List<String> presences = new ArrayList<>();
        private final List<String> humanStates = new ArrayList<>();
        @Override public void saveConnection(RealtimeConnection connection) { }
        @Override public void heartbeat(RealtimeConnection connection) { }
        @Override public void deleteConnection(String tenantId, UUID connectionId) { }
        @Override public void savePresence(String tenantId, String staffId, String status) { presences.add(status); }
        @Override public void saveHumanState(String tenantId, UUID conversationId, String staffId, String status) { humanStates.add(status); }
    }
}
