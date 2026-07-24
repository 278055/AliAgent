package com.bn.aliagent.conversation.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.agent.AgentModels;
import com.bn.aliagent.conversation.agent.AgentModels.Presence;
import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueService;
import com.bn.aliagent.conversation.queue.InMemoryHumanQueueRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AssignmentServiceTest {
    @Test
    void 只选择在线有技能且未满容量的最小负载客服() {
        var fixture = fixture(3);
        fixture.directory.set(List.of(agent(fixture.item.skillGroupId(), "staff-full", Presence.ONLINE, 2, 2, null), agent(fixture.item.skillGroupId(), "staff-busy", Presence.BUSY, 0, 2, null),
                agent(fixture.item.skillGroupId(), "staff-later", Presence.ONLINE, 1, 2, Instant.parse("2026-07-24T01:00:00Z")), agent(fixture.item.skillGroupId(), "staff-low", Presence.ONLINE, 0, 2, Instant.parse("2026-07-24T02:00:00Z"))));

        assertEquals("staff-low", fixture.service.offer(fixture.item.id(), fixture.now).staffId());
    }

    @Test
    void 并发接受只成功一次且重复接受幂等() throws Exception {
        var fixture = fixture(3);
        var offer = fixture.service.offer(fixture.item.id(), fixture.now);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> fixture.service.accept(fixture.tenant, offer.id(), "staff-low", UUID.randomUUID()));
            var two = executor.submit(() -> fixture.service.accept(fixture.tenant, offer.id(), "staff-low", UUID.randomUUID()));
            assertEquals(1, (one.get().accepted() ? 1 : 0) + (two.get().accepted() ? 1 : 0));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, fixture.repository.activeTakeovers(fixture.tenant, fixture.item.conversationId()));
        assertEquals(fixture.service.accept(fixture.tenant, offer.id(), "staff-low", UUID.randomUUID()).takeoverId(), fixture.service.accept(fixture.tenant, offer.id(), "staff-low", UUID.randomUUID()).takeoverId());
    }

    @Test
    void 邀请超时可重入且达到次数后可主动领取() {
        var fixture = fixture(1);
        var offer = fixture.service.offer(fixture.item.id(), fixture.now);
        fixture.service.expire(offer.id(), fixture.now.plusSeconds(31));
        fixture.service.expire(offer.id(), fixture.now.plusSeconds(31));

        assertEquals(HumanQueueModels.QueueStatus.CLAIMABLE, fixture.queue.require(fixture.tenant, fixture.item.id()).status());
        var claimed = fixture.service.claim(fixture.tenant, fixture.item.id(), "staff-low", UUID.randomUUID());
        assertEquals(true, claimed.accepted());
        assertEquals(claimed.takeoverId(), fixture.service.claim(fixture.tenant, fixture.item.id(), "staff-low", UUID.randomUUID()).takeoverId());
    }

    @Test
    void 跨租户拒绝且员工仅可查看所属技能组队列() {
        var fixture = fixture(3);
        var offer = fixture.service.offer(fixture.item.id(), fixture.now);
        assertThrows(IllegalArgumentException.class, () -> fixture.service.accept("foreign", offer.id(), "staff-low", UUID.randomUUID()));
        assertEquals(1, fixture.service.queueForStaff(fixture.tenant, "staff-low").size());
        assertEquals(1, fixture.service.queueForSupervisor(fixture.tenant).size());
    }

    private Fixture fixture(int attempts) {
        var tenant = "tenant-a";
        var group = UUID.randomUUID();
        var now = Instant.parse("2026-07-24T00:00:00Z");
        var queue = new HumanQueueService(new InMemoryHumanQueueRepository(), new HumanQueueModels.PriorityPolicy());
        var item = queue.enqueue(new HumanQueueModels.EnqueueCommand(tenant, UUID.randomUUID(), group, false, false, "NORMAL", now, UUID.randomUUID()));
        var directory = new InMemoryAssignmentDirectory(); directory.set(List.of(agent(group, "staff-low", Presence.ONLINE, 0, 2, null)));
        var repository = new InMemoryAssignmentRepository();
        return new Fixture(tenant, now, item, queue, directory, repository, new AssignmentService(queue, directory, repository, 30, attempts));
    }
    private AgentModels.AgentSnapshot agent(String staff, Presence presence, int active, int max, Instant assigned) { return agent(UUID.randomUUID(), staff, presence, active, max, assigned); }
    private AgentModels.AgentSnapshot agent(UUID group, String staff, Presence presence, int active, int max, Instant assigned) { return new AgentModels.AgentSnapshot("tenant-a", group, staff, presence, true, max, active, assigned); }
    private record Fixture(String tenant, Instant now, HumanQueueModels.QueueItem item, HumanQueueService queue, InMemoryAssignmentDirectory directory, InMemoryAssignmentRepository repository, AssignmentService service) { }
}
