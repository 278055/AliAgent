package com.bn.aliagent.orchestration.copilot;

public final class CopilotSuggestionConsumer {
    private final CopilotService service;
    public CopilotSuggestionConsumer(CopilotService service) { this.service = service; }
    public void accept(CopilotModels.SuggestionRequested event) { service.accept(event); }
}
