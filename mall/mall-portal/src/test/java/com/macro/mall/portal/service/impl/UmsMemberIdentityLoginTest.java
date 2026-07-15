package com.macro.mall.portal.service.impl;

import com.macro.mall.model.UmsMember;
import com.macro.mall.security.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UmsMemberIdentityLoginTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void memberLoginIssuesTrustedDefaultTenantClaims() {
        UmsMember member = new UmsMember();
        member.setId(42L);
        member.setUsername("member-login");
        member.setPassword("encoded-password");
        member.setStatus(1);

        UmsMemberServiceImpl service = spy(new UmsMemberServiceImpl());
        JwtTokenUtil jwtTokenUtil = new JwtTokenUtil("test-signing-key", 300L, "Bearer ");
        ReflectionTestUtils.setField(service, "jwtTokenUtil", jwtTokenUtil);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "tenantId", "dev-tenant");
        doReturn(member).when(service).getByUsername("member-login");
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);

        String token = service.login("member-login", "password");
        Claims claims = jwtTokenUtil.parseAndValidate(token);

        assertNotNull(token);
        assertEquals("42", claims.getSubject());
        assertEquals("MEMBER", claims.get("subjectType", String.class));
        assertEquals("dev-tenant", claims.get("tenantId", String.class));
        assertEquals(Arrays.asList("MEMBER"), claims.get("roles", java.util.List.class));
        assertEquals(Arrays.asList("mall:portal:access"), claims.get("permissions", java.util.List.class));
    }
}
