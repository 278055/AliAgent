package com.bn.aliagent.conversation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bn.aliagent.conversation.queue.HumanQueueModels;
import com.bn.aliagent.conversation.queue.HumanQueueService;
import com.bn.aliagent.conversation.queue.InMemoryHumanQueueRepository;
import com.bn.aliagent.conversation.queue.TrustedHumanQueueEntryService;
import com.bn.aliagent.conversation.queue.VerifiedConversationTagRepository;
import com.bn.aliagent.conversation.skill.SkillGroupModels;
import com.bn.aliagent.conversation.skill.SkillGroupRepository;
import com.bn.aliagent.conversation.skill.SkillRoutingService;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;

class HumanQueueEntryControllerTest {
    @Test
    void 成员请求只传幂等标识而不接收标签() {
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        VerifiedConversationTagRepository tags = new VerifiedConversationTagRepository() {
            public Set<String> verifiedTags(String tenant, UUID conversation) { return Set.of("AFTERSALE_VERIFIED"); }
            public void verify(String tenant, UUID conversation, String tag, String source, Instant at) { }
        };
        SkillGroupRepository groups = new SkillGroupRepository() {
            public Optional<SkillGroupModels.SkillGroup> find(String tenant, UUID id) { return Optional.empty(); }
            public List<SkillGroupModels.SkillGroup> enabledForTags(String tenant, Set<String> tagCodes) {
                return List.of(new SkillGroupModels.SkillGroup(groupId, tenant, "after-sale", "售后", true, "ALL", 50, 30, 3));
            }
        };
        TrustedHumanQueueEntryService queue = new TrustedHumanQueueEntryService(tags, new SkillRoutingService(groups),
                new HumanQueueService(new InMemoryHumanQueueRepository(), new HumanQueueModels.PriorityPolicy()));
        MockHttpServletRequest request = trustedMember();

        var result = new HumanQueueEntryController(queue).enqueue(conversationId, new HumanQueueEntryController.Request(requestId), requestId.toString(), request);

        assertEquals(groupId, ((java.util.Map<?, ?>) result.get("data")).get("skillGroupId"));
    }

    @Test
    void p7路径变量必须显式命名以支持未保留参数名的运行时编译配置() {
        assertPathVariablesNamed(HumanQueueEntryController.class, AgentQueueController.class,
                SupervisorQueueController.class, HumanCollaborationController.class,
                InternalVerifiedTagController.class, InternalCopilotContextController.class,
                InternalCopilotStaffMessageController.class, SkillGroupAdministrationController.class);
    }

    private void assertPathVariablesNamed(Class<?>... controllers) {
        for (Class<?> controller : controllers) {
            for (var method : controller.getDeclaredMethods()) {
                for (var parameter : method.getParameters()) {
                    PathVariable variable = parameter.getAnnotation(PathVariable.class);
                    if (variable != null) {
                        boolean named = !variable.value().isBlank() || !variable.name().isBlank();
                        assertEquals(true, named, controller.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
    }

    private MockHttpServletRequest trustedMember() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("com.bn.platform.security.ServiceJwtAuthenticationFilter.verified", Boolean.TRUE);
        request.addHeader("X-Tenant-Id", "tenant-a"); request.addHeader("X-Subject-Id", "member-a"); request.addHeader("X-Subject-Type", "MEMBER");
        request.addHeader("X-Trace-Id", "trace-a"); request.addHeader("X-Request-Id", UUID.randomUUID().toString());
        return request;
    }
}
