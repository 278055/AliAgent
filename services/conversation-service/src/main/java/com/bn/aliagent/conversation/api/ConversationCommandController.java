package com.bn.aliagent.conversation.api;
import com.bn.aliagent.conversation.core.*;
import com.bn.aliagent.conversation.core.ConversationApi.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @org.springframework.context.annotation.Profile("database") @RequestMapping("/api/v1/conversations") public class ConversationCommandController {
 private final ConversationService service; public ConversationCommandController(ConversationService service){this.service=service;}
 @PostMapping public Map<String,Object> create(@RequestBody CreateConversation body,HttpServletRequest r){var c=service.create(TrustedConversationRequestContext.from(r),body.title());return ok(view(c));}
 @PatchMapping("/{id}") public Map<String,Object> patch(@PathVariable UUID id,@RequestBody PatchConversation b,HttpServletRequest r){var c=service.patch(TrustedConversationRequestContext.from(r),id,b.title(),b.pinned(),b.closed());return ok(view(c));}
 @DeleteMapping("/{id}") public Map<String,Object> delete(@PathVariable UUID id,HttpServletRequest r){service.delete(TrustedConversationRequestContext.from(r),id);return ok(Map.of());}
 @PostMapping("/{id}/messages") public Map<String,Object> submit(@PathVariable UUID id,@RequestBody SubmitMessage b,@RequestHeader("Idempotency-Key") String key,HttpServletRequest r){if(b.content()==null||b.content().isBlank())throw new ConversationException("CONV-400-002","content 不能为空"); var m=service.submit(TrustedConversationRequestContext.from(r),id,b.content(),b.requestId(),key);return ok(Map.of("accepted",true,"message",new MessageView(m.id(),m.sequence(),m.content(),m.status(),m.requestId())));}
 private ConversationView view(ConversationModels.Conversation c){return new ConversationView(c.id(),c.title(),c.status(),c.pinned());} private Map<String,Object> ok(Object data){return Map.of("code",200,"message","","data",data);}
}
