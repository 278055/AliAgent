package com.bn.aliagent.orchestration.copilot;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;

/** 副驾建议仅向当前接管客服暴露，客服身份始终取自网关注入的可信请求头。 */
@RestController
@Profile("database")
@RequestMapping("/api/v1/copilot/conversations/{conversationId}/suggestions")
public class CopilotSuggestionController {
    private final CopilotService service;
    private final CopilotRepository repository;

    public CopilotSuggestionController(CopilotService service, CopilotRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> list(@PathVariable("conversationId") UUID conversationId, HttpServletRequest request) {
        TrustedStaff staff = staff(request);
        var suggestions = repository.findForConversation(staff.tenantId(), conversationId, staff.id()).stream()
                .map(this::view).toList();
        return ok(Map.of("items", suggestions));
    }

    @PostMapping("/{suggestionId}:accept")
    public Map<String, Object> accept(@PathVariable("conversationId") UUID conversationId, @PathVariable("suggestionId") UUID suggestionId,
                                      @RequestBody ActionRequest body, HttpServletRequest request) {
        return action(conversationId, suggestionId, body, request, CopilotModels.ActionType.ACCEPTED);
    }

    @PostMapping("/{suggestionId}:modify")
    public Map<String, Object> modify(@PathVariable("conversationId") UUID conversationId, @PathVariable("suggestionId") UUID suggestionId,
                                      @RequestBody ActionRequest body, HttpServletRequest request) {
        if (body == null || body.content() == null || body.content().isBlank()) throw new CopilotException("编辑后的建议不能为空");
        return action(conversationId, suggestionId, body, request, CopilotModels.ActionType.MODIFIED);
    }

    @PostMapping("/{suggestionId}:ignore")
    public Map<String, Object> ignore(@PathVariable("conversationId") UUID conversationId, @PathVariable("suggestionId") UUID suggestionId,
                                      @RequestBody ActionRequest body, HttpServletRequest request) {
        return action(conversationId, suggestionId, body, request, CopilotModels.ActionType.IGNORED);
    }

    private Map<String, Object> action(UUID conversationId, UUID suggestionId, ActionRequest body,
                                       HttpServletRequest request, CopilotModels.ActionType type) {
        TrustedStaff staff = staff(request);
        if (body == null || body.requestId() == null) throw new CopilotException("requestId is required");
        var suggestion = repository.findSuggestion(suggestionId).orElseThrow(() -> new CopilotException("建议不存在"));
        if (!conversationId.equals(suggestion.conversationId())) throw new CopilotException("建议不属于当前会话");
        var command = new CopilotModels.ActionCommand(body.requestId(), staff.tenantId(), suggestionId, staff.id(), body.content());
        CopilotModels.Action result = switch (type) {
            case ACCEPTED -> service.accept(command);
            case MODIFIED -> service.modifyAndSend(command);
            case IGNORED -> service.ignore(command);
        };
        return ok(Map.of("suggestionId", result.suggestionId(), "actionType", result.type().name(), "finalContent", result.finalContent()));
    }

    private TrustedStaff staff(HttpServletRequest request) {
        if (!Boolean.TRUE.equals(request.getAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE))) {
            throw new CopilotException("Service authentication is invalid");
        }
        String tenantId = required(request, "X-Tenant-Id");
        String subjectId = required(request, "X-Subject-Id");
        if (!"STAFF".equals(required(request, "X-Subject-Type"))) throw new CopilotException("STAFF role is required");
        return new TrustedStaff(tenantId, subjectId);
    }

    private String required(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw new CopilotException("trusted identity context is required");
        return value;
    }

    private Map<String, Object> view(CopilotModels.Suggestion value) {
        return Map.of("id", value.suggestionId(), "conversationId", value.conversationId(), "status", value.status().name(),
                "content", value.finalContent().isBlank() ? value.originalContent() : value.finalContent(), "citations", value.citations());
    }

    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "message", "", "data", data); }

    public record ActionRequest(UUID requestId, String content) { }
    private record TrustedStaff(String tenantId, String id) { }
}
