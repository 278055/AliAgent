package com.bn.aliagent.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

class RequestContextLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestContextLoggingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String requestId = valueOrGenerated(request.getHeader("X-Request-Id"));
        String traceId = valueOrGenerated(request.getHeader("X-Trace-Id"));
        String tenantId = request.getHeader("X-Tenant-Id");
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Trace-Id", traceId);
        log.info("收到请求 traceId={}, requestId={}, tenantId={}, service=gateway-service", traceId, requestId, tenantId);
        chain.doFilter(request, response);
    }
    private String valueOrGenerated(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value; }
}
