package com.macro.mall.portal.internal.read;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class InternalReadExceptionHandler {
    @ExceptionHandler(InternalAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(HttpServletRequest request) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "MALL-403-001");
        body.put("message", "resource access denied");
        body.put("requestId", request.getHeader("X-Request-Id"));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
