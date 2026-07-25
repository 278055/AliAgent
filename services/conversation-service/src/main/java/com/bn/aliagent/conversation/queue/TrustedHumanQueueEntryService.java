package com.bn.aliagent.conversation.queue;

import com.bn.aliagent.conversation.skill.SkillRoutingService;
import java.time.Instant;
import java.util.UUID;

/** 公共入队路径不接收标签，只使用内部验证标签事实。 */
public final class TrustedHumanQueueEntryService {
    private final VerifiedConversationTagRepository tags;
    private final SkillRoutingService routing;
    private final HumanQueueService queues;

    public TrustedHumanQueueEntryService(VerifiedConversationTagRepository tags, SkillRoutingService routing, HumanQueueService queues) {
        this.tags = tags; this.routing = routing; this.queues = queues;
    }

    public HumanQueueModels.QueueItem enqueue(String tenantId, UUID conversationId, UUID requestId, Instant now) {
        var route = routing.route(tenantId, tags.verifiedTags(tenantId, conversationId));
        return queues.enqueue(new HumanQueueModels.EnqueueCommand(tenantId, conversationId, route.skillGroupId(), false, false, "NORMAL", now, requestId));
    }
}
