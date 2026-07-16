package com.bn.aliagent.knowledge.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TrustedKnowledgeContextFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/v1/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            if (!Boolean.TRUE.equals(request.getAttribute(ServiceJwtAuthenticationFilter.VERIFIED_ATTRIBUTE))) {
                throw new IllegalArgumentException("服务认证未完成");
            }
            TrustedKnowledgeRequestContext.bind(request);
            chain.doFilter(request, response);
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"TENANT-400-001\",\"message\":\"缺少可信请求上下文\",\"requestId\":\"\"}");
        }
    }
}
