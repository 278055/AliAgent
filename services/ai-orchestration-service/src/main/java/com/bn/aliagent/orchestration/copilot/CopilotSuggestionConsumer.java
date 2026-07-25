package com.bn.aliagent.orchestration.copilot;

import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public final class CopilotSuggestionConsumer {
    private final CopilotService service;
    public CopilotSuggestionConsumer(CopilotService service) { this.service = service; }
    public void accept(CopilotModels.SuggestionRequested event) { service.accept(event); }
    @RabbitListener(queues = "${orchestration.messaging.copilot-requested-queue:copilot.suggestion.requested.v2}")
    public void consume(Map<String, Object> event) { accept(new CopilotSuggestionRequestedMapper().map(event)); }
}
