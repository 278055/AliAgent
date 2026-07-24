package com.bn.aliagent.conversation.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HumanQueueServiceTest {
    @Test
    void 同一请求和会话重复入队返回同一个队列项() {
        var repository = new InMemoryHumanQueueRepository();
        var service = new HumanQueueService(repository, new HumanQueueModels.PriorityPolicy());
        var command = command("tenant-a");

        var first = service.enqueue(command);
        var second = service.enqueue(command);

        assertEquals(first.id(), second.id());
        assertEquals(1, repository.activeCount(command.tenantId(), command.conversationId()));
    }

    @Test
    void 跨租户不可读取队列项() {
        var service = new HumanQueueService(new InMemoryHumanQueueRepository(), new HumanQueueModels.PriorityPolicy());
        var item = service.enqueue(command("tenant-a"));

        assertThrows(IllegalArgumentException.class, () -> service.require("tenant-b", item.id()));
    }

    @Test
    void 同分队列按入队时间先进先出() {
        var repository = new InMemoryHumanQueueRepository();
        var service = new HumanQueueService(repository, new HumanQueueModels.PriorityPolicy());
        var first = command("tenant-a");
        var second = new HumanQueueModels.EnqueueCommand("tenant-a", UUID.randomUUID(), UUID.randomUUID(), false, false,
                "NORMAL", first.enqueuedAt().plusSeconds(1), UUID.randomUUID());

        service.enqueue(second);
        service.enqueue(first);

        assertEquals(first.conversationId(), repository.list("tenant-a").get(0).conversationId());
    }

    private HumanQueueModels.EnqueueCommand command(String tenant) {
        return new HumanQueueModels.EnqueueCommand(tenant, UUID.randomUUID(), UUID.randomUUID(), false, false,
                "NORMAL", Instant.parse("2026-07-24T00:00:00Z"), UUID.randomUUID());
    }
}
