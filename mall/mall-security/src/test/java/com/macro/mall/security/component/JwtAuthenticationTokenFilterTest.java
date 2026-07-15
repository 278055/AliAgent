package com.macro.mall.security.component;

import com.macro.mall.security.identity.IdentityTokenClaims;
import com.macro.mall.security.identity.SubjectType;
import com.macro.mall.security.util.JwtTokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JwtAuthenticationTokenFilterTest {

    private final JwtTokenUtil tokenUtil = new JwtTokenUtil("test-signing-key", 300L, "Bearer ");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateTamperedToken() throws Exception {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "42", "member-login", SubjectType.MEMBER, "dev-tenant",
                Arrays.asList("MEMBER"), Arrays.asList("mall:portal:access")));
        String tamperedToken = tamperPayload(token);

        assertRejected(tamperedToken);
    }

    @Test
    void doesNotAuthenticateExpiredToken() throws Exception {
        JwtTokenUtil expiredTokenUtil = new JwtTokenUtil("test-signing-key", -1L, "Bearer ");
        String token = expiredTokenUtil.generateToken(IdentityTokenClaims.of(
                "42", "member-login", SubjectType.MEMBER, "dev-tenant",
                Arrays.asList("MEMBER"), Arrays.asList("mall:portal:access")));

        assertRejected(token);
    }

    @Test
    void doesNotAuthenticateStaffTokenAtMemberEntry() throws Exception {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "7", "staff-login", SubjectType.STAFF, "dev-tenant",
                Arrays.asList("客服"), Arrays.asList("9:订单查询")));

        assertRejected(token);
    }

    private void assertRejected(String token) throws Exception {
        JwtAuthenticationTokenFilter filter = new JwtAuthenticationTokenFilter();
        ReflectionTestUtils.setField(filter, "jwtTokenUtil", tokenUtil);
        ReflectionTestUtils.setField(filter, "tokenHeader", "Authorization");
        ReflectionTestUtils.setField(filter, "tokenHead", "Bearer ");
        ReflectionTestUtils.setField(filter, "subjectType", SubjectType.MEMBER);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        AtomicBoolean unauthenticated = new AtomicBoolean(false);

        filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) ->
                unauthenticated.set(SecurityContextHolder.getContext().getAuthentication() == null));

        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
        assertFalse(!unauthenticated.get());
    }

    private String tamperPayload(String token) {
        int payloadStart = token.indexOf('.') + 1;
        char original = token.charAt(payloadStart);
        char replacement = original == 'A' ? 'B' : 'A';
        return token.substring(0, payloadStart) + replacement + token.substring(payloadStart + 1);
    }
}
