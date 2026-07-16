package com.bn.aliagent.knowledge.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
class KnowledgeApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeApiExceptionHandler.class);
    @ExceptionHandler({KnowledgeResourceNotFoundException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, Object> knowledgeConflict() {
        return Map.of("code", "KNOWLEDGE-409-001", "message", "知识资源状态不允许该操作", "requestId", UUID.randomUUID().toString());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> invalidRequest(IllegalArgumentException exception) {
        LOG.warn("知识请求参数无效: {}", exception.getMessage());
        return Map.of("code", "KNOWLEDGE-400-001", "message", "知识请求无效", "requestId", UUID.randomUUID().toString());
    }
}
