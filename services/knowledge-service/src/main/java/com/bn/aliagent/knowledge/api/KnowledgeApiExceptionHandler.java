package com.bn.aliagent.knowledge.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class KnowledgeApiExceptionHandler {
    @ExceptionHandler({KnowledgeResourceNotFoundException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> knowledgeConflict() {
        return Map.of("code", "KNOWLEDGE-409-001", "message", "知识资源状态不允许该操作", "requestId", UUID.randomUUID().toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> invalidRequest() {
        return Map.of("code", "KNOWLEDGE-400-001", "message", "知识请求无效", "requestId", UUID.randomUUID().toString());
    }
}
