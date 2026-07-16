package com.bn.aliagent.knowledge.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.bn.platform.security.ServiceJwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class TrustedKnowledgeContextFilterConfiguration {
    @Bean
    FilterRegistrationBean<TrustedKnowledgeContextFilter> trustedKnowledgeContextFilterRegistration() {
        FilterRegistrationBean<TrustedKnowledgeContextFilter> registration = new FilterRegistrationBean<>(new TrustedKnowledgeContextFilter());
        registration.setOrder(2);
        return registration;
    }
}

class TrustedKnowledgeContextFilter extends OncePerRequestFilter {
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
