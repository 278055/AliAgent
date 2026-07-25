package com.bn.aliagent.conversation.realtime;

import com.bn.aliagent.conversation.messaging.HumanCollaborationEvent;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public final class HumanAgentRealtimePublisher {
    private final RealtimeCollaborationService realtime;
    public HumanAgentRealtimePublisher(RealtimeCollaborationService realtime) { this.realtime = realtime; }
    @RabbitListener(queues = "conversation.human.events.v1")
    public void publish(HumanCollaborationEvent event) {
        realtime.deliver(new RealtimeEnvelope(event.eventType(), event.tenantId(), event.conversationId(), event.requestId(), event.occurredAt(), null, 0, event.sender(), event.content(), null, null, null, event.status()));
    }
}
