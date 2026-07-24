package com.bn.aliagent.conversation.takeover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TakeoverServiceTest {
    private static final String TENANT = "tenant-a";
    private static final UUID CONVERSATION = UUID.randomUUID();

    @Test
    void 同一会话并发接管只能建立一个active事实() {
        Repository repository = new Repository();
        TakeoverService service = new TakeoverService(repository);

        long accepted = IntStream.range(0, 12).parallel().mapToObj(index -> service.takeOver(
                new TakeoverCommand(TENANT, CONVERSATION, "staff-" + index, UUID.randomUUID(), "ACCEPTED_OFFER")))
                .filter(TakeoverResult::accepted).count();

        assertEquals(1, accepted);
        assertEquals(1, repository.activeCount(TENANT, CONVERSATION));
    }

    @Test
    void 重复请求返回同一接管且无邀请和跨租户被拒绝() {
        Repository repository = new Repository();
        TakeoverService service = new TakeoverService(repository);
        UUID requestId = UUID.randomUUID();
        TakeoverCommand command = new TakeoverCommand(TENANT, CONVERSATION, "staff-1", requestId, "ACCEPTED_OFFER");

        assertEquals(service.takeOver(command).takeover().id(), service.takeOver(command).takeover().id());
        assertThrows(TakeoverException.class, () -> service.takeOver(new TakeoverCommand(TENANT, CONVERSATION,
                "staff-2", UUID.randomUUID(), "NONE")));
        assertThrows(TakeoverException.class, () -> service.takeOver(new TakeoverCommand("tenant-b", CONVERSATION,
                "staff-2", UUID.randomUUID(), "ACCEPTED_OFFER")));
    }

    private static final class Repository implements TakeoverRepository {
        private final Map<UUID, Takeover> byRequest = new ConcurrentHashMap<>();
        private final Map<String, Takeover> active = new ConcurrentHashMap<>();
        public boolean conversationExists(String tenantId, UUID conversationId) { return TENANT.equals(tenantId) && CONVERSATION.equals(conversationId); }
        public boolean acceptedOrClaimed(TakeoverCommand command) { return "ACCEPTED_OFFER".equals(command.evidence()); }
        public Takeover findByRequest(UUID requestId) { return byRequest.get(requestId); }
        public synchronized Takeover createActiveIfAbsent(Takeover takeover) {
            Takeover current = active.putIfAbsent(takeover.tenantId() + takeover.conversationId(), takeover);
            byRequest.putIfAbsent(takeover.requestId(), current == null ? takeover : current);
            return current == null ? takeover : current;
        }
        int activeCount(String tenantId, UUID conversationId) { return active.containsKey(tenantId + conversationId) ? 1 : 0; }
    }
}
