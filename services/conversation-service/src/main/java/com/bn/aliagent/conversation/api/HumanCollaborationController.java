package com.bn.aliagent.conversation.api;

import com.bn.aliagent.conversation.core.ConversationException;
import com.bn.aliagent.conversation.core.TrustedConversationRequestContext;
import com.bn.aliagent.conversation.handoff.HandoffCommand;
import com.bn.aliagent.conversation.handoff.HandoffService;
import com.bn.aliagent.conversation.staffmessage.StaffMessageCommand;
import com.bn.aliagent.conversation.staffmessage.StaffMessageService;
import com.bn.aliagent.conversation.transfer.AgentTransferCommand;
import com.bn.aliagent.conversation.transfer.SkillGroupTransferCommand;
import com.bn.aliagent.conversation.transfer.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database")
@RequestMapping("/api/v1/conversations")
public class HumanCollaborationController {
    private final StaffMessageService messages;
    private final TransferService transfers;
    private final HandoffService handoffs;
    public HumanCollaborationController(StaffMessageService messages, TransferService transfers, HandoffService handoffs) { this.messages = messages; this.transfers = transfers; this.handoffs = handoffs; }

    @PostMapping("/{conversationId}/human-messages")
    public Map<String, Object> message(@PathVariable("conversationId") UUID conversationId, @RequestBody Message body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var context = staff(request); UUID requestId = requestId(body.requestId(), key);
        var value = messages.send(new StaffMessageCommand(context.tenantId(), conversationId, context.subjectId(), body.content(), body.clientMessageId(), requestId), context.authorizationSnapshotId());
        return ok(Map.of("id", value.id(), "sequence", value.clientMessageId(), "content", value.content()));
    }

    @PostMapping("/{conversationId}/transfer/agent")
    public Map<String, Object> transferAgent(@PathVariable("conversationId") UUID conversationId, @RequestBody AgentTransfer body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var context = staff(request); return ok(transfers.requestAgentTransfer(new AgentTransferCommand(context.tenantId(), conversationId, context.subjectId(), body.targetStaffId(), requestId(body.requestId(), key), false, null)));
    }
    @PostMapping("/{conversationId}/transfer/skill-group")
    public Map<String, Object> transferGroup(@PathVariable("conversationId") UUID conversationId, @RequestBody GroupTransfer body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var context = staff(request); return ok(transfers.transferToSkillGroup(new SkillGroupTransferCommand(context.tenantId(), conversationId, context.subjectId(), body.skillGroupId(), requestId(body.requestId(), key), false, null)));
    }
    @PostMapping("/{conversationId}/human-release")
    public Map<String, Object> release(@PathVariable("conversationId") UUID conversationId, @RequestBody Request body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var context = staff(request); return ok(handoffs.release(new HandoffCommand(context.tenantId(), conversationId, context.subjectId(), requestId(body.requestId(), key), false, null)));
    }
    @PostMapping("/{conversationId}/human-close")
    public Map<String, Object> close(@PathVariable("conversationId") UUID conversationId, @RequestBody Request body, @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var context = staff(request); return ok(handoffs.close(new HandoffCommand(context.tenantId(), conversationId, context.subjectId(), requestId(body.requestId(), key), false, null)));
    }
    private TrustedConversationRequestContext staff(HttpServletRequest request) { var context=TrustedConversationRequestContext.from(request); if(!"STAFF".equals(context.subjectType())) throw new ConversationException("AUTH-403-001","STAFF role is required"); return context; }
    private UUID requestId(UUID value,String key){ if(value==null||key==null||!key.equals(value.toString()))throw new ConversationException("CONV-400-004","Idempotency-Key must equal requestId"); return value; }
    private Map<String,Object> ok(Object data){return Map.of("code",200,"message","","data",data);}
    public record Request(UUID requestId) { }
    public record Message(String content, UUID clientMessageId, UUID requestId) { }
    public record AgentTransfer(String targetStaffId, UUID requestId) { }
    public record GroupTransfer(String skillGroupId, UUID requestId) { }
}
