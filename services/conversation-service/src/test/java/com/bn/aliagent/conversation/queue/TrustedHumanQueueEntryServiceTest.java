package com.bn.aliagent.conversation.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bn.aliagent.conversation.skill.SkillGroupModels;
import com.bn.aliagent.conversation.skill.SkillGroupRepository;
import com.bn.aliagent.conversation.skill.SkillRoutingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrustedHumanQueueEntryServiceTest {
    @Test
    void 仅用持久化的已验证标签入队() {
        UUID groupId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        InMemoryHumanQueueRepository queue = new InMemoryHumanQueueRepository();
        VerifiedConversationTagRepository tags = tags(Set.of("AFTERSALE_VERIFIED"));
        SkillGroupRepository groups = new SkillGroupRepository() {
            public Optional<SkillGroupModels.SkillGroup> find(String tenant, UUID id) { return Optional.empty(); }
            public List<SkillGroupModels.SkillGroup> enabledForTags(String tenant, Set<String> values) { return List.of(new SkillGroupModels.SkillGroup(groupId, tenant, "after-sale", "售后", true, "ALL", 50, 30, 3)); }
        };

        var result = new TrustedHumanQueueEntryService(tags, new SkillRoutingService(groups), new HumanQueueService(queue, new HumanQueueModels.PriorityPolicy()))
                .enqueue("tenant-a", conversationId, UUID.randomUUID(), Instant.now());

        assertEquals(groupId, result.skillGroupId());
    }

    @Test
    void 没有已验证标签时拒绝入队而非接受客户端声明() {
        VerifiedConversationTagRepository tags = tags(Set.of());
        SkillGroupRepository groups = new SkillGroupRepository() {
            public Optional<SkillGroupModels.SkillGroup> find(String tenant, UUID id) { return Optional.empty(); }
            public List<SkillGroupModels.SkillGroup> enabledForTags(String tenant, Set<String> values) { return List.of(); }
        };
        var service = new TrustedHumanQueueEntryService(tags, new SkillRoutingService(groups), new HumanQueueService(new InMemoryHumanQueueRepository(), new HumanQueueModels.PriorityPolicy()));

        assertThrows(IllegalArgumentException.class, () -> service.enqueue("tenant-a", UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
    }

    private VerifiedConversationTagRepository tags(Set<String> values) {
        return new VerifiedConversationTagRepository() {
            public Set<String> verifiedTags(String tenant, UUID conversation) { return values; }
            public void verify(String tenant, UUID conversation, String tag, String source, Instant at) { }
        };
    }
}
