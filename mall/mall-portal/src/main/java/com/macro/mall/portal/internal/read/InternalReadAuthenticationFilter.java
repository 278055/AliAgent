package com.macro.mall.portal.internal.read;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public class InternalReadAuthenticationFilter extends OncePerRequestFilter {
    private static final String PATH_PREFIX = "/api/v1/internal/mall/";
    private static final String AUDIENCE = "mall";
    private static final String SCOPE = "mall.internal.read";

    private final HeaderUserSnapshotResolver userSnapshotResolver;
    private final ServiceIdentityVerifier serviceIdentityVerifier;

    public InternalReadAuthenticationFilter(HeaderUserSnapshotResolver userSnapshotResolver,
                                            ServiceIdentityVerifier serviceIdentityVerifier) {
        this.userSnapshotResolver = userSnapshotResolver;
        this.serviceIdentityVerifier = serviceIdentityVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            serviceIdentityVerifier.verify(request.getHeader("X-Service-Authorization"), AUDIENCE, SCOPE);
            UserSnapshot snapshot = userSnapshotResolver.resolve(request);
            request.setAttribute(UserSnapshot.REQUEST_ATTRIBUTE, snapshot);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(snapshot, null, Collections.emptyList()));
            filterChain.doFilter(request, response);
        } catch (InternalAuthenticationException exception) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"AUTH-401-001\",\"message\":\"internal authentication failed\"}");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
