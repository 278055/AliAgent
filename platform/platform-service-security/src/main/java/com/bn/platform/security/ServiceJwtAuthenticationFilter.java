package com.bn.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ServiceJwtAuthenticationFilter extends OncePerRequestFilter {
    private final ServiceJwtSupport jwtSupport;
    private final String serviceName;

    public ServiceJwtAuthenticationFilter(ServiceJwtSupport jwtSupport, String serviceName) {
        this.jwtSupport = jwtSupport;
        this.serviceName = serviceName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/v1/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            String authorization = request.getHeader("X-Service-Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("Missing service JWT");
            jwtSupport.verify(authorization.substring(7), serviceName, request.getMethod() + ":" + request.getRequestURI());
            chain.doFilter(request, response);
        } catch (Exception exception) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
            response.getWriter().write("{\"code\":\"AUTH-401-001\",\"message\":\"服务认证无效\",\"requestId\":\"" + requestId + "\"}");
        }
    }
}
