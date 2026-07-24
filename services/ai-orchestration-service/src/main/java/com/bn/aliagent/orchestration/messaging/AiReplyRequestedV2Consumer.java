package com.bn.aliagent.orchestration.messaging;

import com.bn.aliagent.orchestration.core.OrchestrationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.Map;

public final class AiReplyRequestedV2Consumer {
    private final AiReplyRequestedV2Mapper mapper;
    private final OrchestrationService service;

    public AiReplyRequestedV2Consumer(AiReplyRequestedV2Mapper mapper, OrchestrationService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @RabbitListener(queues = "${orchestration.messaging.ai-reply.v2-queue:ai.reply.requested.v2}")
    public void consume(Map<String, Object> event) {
        service.accept(mapper.map(event), "");
    }
}
