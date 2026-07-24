package com.bn.aliagent.conversation.api;
import com.bn.aliagent.conversation.core.ConversationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ConversationApiExceptionHandler {
 @ExceptionHandler(ConversationException.class) ResponseEntity<?> handle(ConversationException e,HttpServletRequest r){return ResponseEntity.badRequest().body(Map.of("code",e.code(),"message",e.getMessage(),"requestId",r.getHeader("X-Request-Id")));}
}
