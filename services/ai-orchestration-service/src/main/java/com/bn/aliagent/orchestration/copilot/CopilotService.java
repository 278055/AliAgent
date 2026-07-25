package com.bn.aliagent.orchestration.copilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CopilotService {
    private final CopilotRepository repository;
    private final CopilotPorts.ConversationContextPort conversations;
    private final CopilotPorts.KnowledgeContextPort knowledge;
    private final CopilotPorts.CommerceFactPort commerce;
    private final CopilotPorts.AfterSaleFactPort afterSale;
    private final CopilotPorts.CopilotModelPort model;
    private final CopilotPromptFactory prompts;
    private final CopilotPorts.StaffMessagePort messages;
    private int modelCalls;
    public CopilotService(CopilotRepository repository, CopilotPorts.ConversationContextPort conversations,
                          CopilotPorts.KnowledgeContextPort knowledge, CopilotPorts.CommerceFactPort commerce,
                          CopilotPorts.AfterSaleFactPort afterSale, CopilotPorts.CopilotModelPort model,
                          CopilotPromptFactory prompts, CopilotPorts.StaffMessagePort messages) {
        this.repository = repository; this.conversations = conversations; this.knowledge = knowledge; this.commerce = commerce;
        this.afterSale = afterSale; this.model = model; this.prompts = prompts; this.messages = messages;
    }
    public int modelCalls() { return modelCalls; }
    public void accept(CopilotModels.SuggestionRequested event) {
        if (!repository.claimInbox(event.eventId())) return;
        try {
            generate(event.command());
            repository.completeInbox(event.eventId());
        } catch (RuntimeException failure) {
            generateFailed(event.command());
            repository.completeInbox(event.eventId());
        }
    }
    public CopilotModels.Suggestion generate(CopilotModels.GenerateCommand command) {
        var existing = repository.findByRequestId(command.requestId());
        if (existing.isPresent()) return existing.get();
        var conversation = conversations.load(command.tenantId(), command.conversationId());
        if (!command.tenantId().equals(conversation.tenantId()) || !command.conversationId().equals(conversation.conversationId())) throw new CopilotException("会话上下文不属于当前租户");
        if (!"HUMAN_ACTIVE".equals(conversation.collaborationState()) || !command.assignedAgentId().equals(command.currentAgentId())
                || !command.assignedAgentId().equals(conversation.assignedAgentId())) throw new CopilotException("当前客服未接管该会话");
        try {
            String query = conversation.messages().isEmpty() ? "" : conversation.messages().get(conversation.messages().size() - 1);
            List<CopilotModels.Citation> citations = knowledge.retrieve(command.tenantId(), command.conversationId(),
                    command.authorizationSnapshotId(), command.subjectId(), command.subjectType(), command.roles(),
                    command.permissions(), query);
            var context = new CopilotModels.PromptContext(conversation, citations, commerce.read(command.tenantId(), command.conversationId()), afterSale.read(command.tenantId(), command.conversationId()));
            modelCalls++;
            var generated = create(command, CopilotModels.SuggestionStatus.GENERATED, sanitizeModelOutput(model.generate(prompts.create(context), context)), citations);
            repository.save(generated, command.requestId()); return generated;
        } catch (RuntimeException dependencyFailure) { return generateFailed(command); }
    }
    public CopilotModels.Action accept(CopilotModels.ActionCommand command) { return act(command, CopilotModels.ActionType.ACCEPTED, null); }
    public CopilotModels.Action modifyAndSend(CopilotModels.ActionCommand command) { return act(command, CopilotModels.ActionType.MODIFIED, command.content()); }
    public CopilotModels.Action ignore(CopilotModels.ActionCommand command) { return act(command, CopilotModels.ActionType.IGNORED, null); }
    private synchronized CopilotModels.Action act(CopilotModels.ActionCommand command, CopilotModels.ActionType type, String edited) {
        var old = repository.findAction(command.requestId()); if (old.isPresent()) return old.get();
        var suggestion = repository.findSuggestion(command.suggestionId()).orElseThrow(() -> new CopilotException("建议不存在"));
        if (!suggestion.tenantId().equals(command.tenantId()) || !suggestion.assignedAgentId().equals(command.agentId()) || suggestion.status() != CopilotModels.SuggestionStatus.GENERATED) throw new CopilotException("建议不可操作");
        var conversation = conversations.load(command.tenantId(), suggestion.conversationId());
        if (!command.tenantId().equals(conversation.tenantId()) || !suggestion.conversationId().equals(conversation.conversationId())
                || !"HUMAN_ACTIVE".equals(conversation.collaborationState()) || !command.agentId().equals(conversation.assignedAgentId())) throw new CopilotException("当前客服未接管该会话");
        var finalContent = type == CopilotModels.ActionType.MODIFIED ? edited : suggestion.originalContent();
        var diff = type == CopilotModels.ActionType.MODIFIED ? "内容已由客服编辑" : "";
        if (type != CopilotModels.ActionType.IGNORED) messages.send(command.tenantId(), suggestion.conversationId(), command.agentId(), finalContent, command.requestId());
        var action = new CopilotModels.Action(command.requestId(), suggestion.suggestionId(), type, suggestion.originalContent(), finalContent, diff);
        repository.saveAction(action);
        var status = type == CopilotModels.ActionType.ACCEPTED ? CopilotModels.SuggestionStatus.ACCEPTED : type == CopilotModels.ActionType.MODIFIED ? CopilotModels.SuggestionStatus.MODIFIED : CopilotModels.SuggestionStatus.IGNORED;
        repository.save(suggestion.withStatus(status, finalContent, diff), command.requestId());
        return action;
    }
    private CopilotModels.Suggestion create(CopilotModels.GenerateCommand c, CopilotModels.SuggestionStatus status, String content, List<CopilotModels.Citation> citations) {
        return new CopilotModels.Suggestion(UUID.randomUUID(), c.tenantId(), c.conversationId(), c.triggerMessageId(), c.assignedAgentId(), c.refreshNo(), CopilotModels.Visibility.PRIVATE, status, content, "", "", c.modelVersion(), c.promptVersion(), c.workflowVersion(), citations, Instant.now());
    }
    private CopilotModels.Suggestion generateFailed(CopilotModels.GenerateCommand command) {
        var existing = repository.findByRequestId(command.requestId());
        if (existing.isPresent()) return existing.get();
        var failed = create(command, CopilotModels.SuggestionStatus.FAILED_RETRYABLE, "", List.of());
        repository.save(failed, command.requestId());
        return failed;
    }
    private String sanitizeModelOutput(String content) {
        if (content == null) throw new CopilotException("模型未返回建议");
        String sanitized = content.replaceAll("(?is)<think>.*?</think>", "").replaceAll("(?im)^\s*(思维过程|推理过程|reasoning)\s*[:：].*$", "").trim();
        if (sanitized.isBlank() || sanitized.contains("<think>")) throw new CopilotException("模型输出包含不可持久化内容");
        return sanitized;
    }
}
