package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.skill.SkillGroupAdministrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/supervisor/skill-groups")
public class SkillGroupAdministrationController {
    private final SkillGroupAdministrationService groups;
    public SkillGroupAdministrationController(SkillGroupAdministrationService groups) { this.groups = groups; }
    @PostMapping public Map<String, Object> create(@RequestBody Create body, HttpServletRequest request) {
        String tenant = supervisor(request);
        var group = groups.create(tenant, body == null ? null : body.name());
        return ok(Map.of("id", group.id(), "name", group.name()));
    }
    @PutMapping("/{skillGroupId}/tags") public Map<String, Object> tags(@PathVariable("skillGroupId") UUID skillGroupId, @RequestBody Tags body, HttpServletRequest request) {
        groups.replaceTags(supervisor(request), skillGroupId, body == null ? null : body.tagCodes()); return ok(Map.of("updated", true));
    }
    @PutMapping("/{skillGroupId}/members/{staffId}") public Map<String, Object> member(@PathVariable("skillGroupId") UUID skillGroupId, @PathVariable("staffId") String staffId, @RequestBody Member body, HttpServletRequest request) {
        groups.upsertMember(supervisor(request), skillGroupId, staffId, body == null ? 0 : body.maxConcurrent(), body != null && body.enabled()); return ok(Map.of("updated", true));
    }
    private String supervisor(HttpServletRequest request) {
        var context = TrustedConversationRequestContext.from(request);
        String roles = request.getHeader("X-User-Roles");
        if (!"STAFF".equals(context.subjectType()) || roles == null || !Set.of(roles.split(",")).contains("SUPERVISOR")) throw new ConversationException("AUTH-403-001", "SUPERVISOR role is required");
        return context.tenantId();
    }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "message", "", "data", data); }
    public record Create(String name) { }
    public record Tags(Set<String> tagCodes) { }
    public record Member(int maxConcurrent, boolean enabled) { }
}
