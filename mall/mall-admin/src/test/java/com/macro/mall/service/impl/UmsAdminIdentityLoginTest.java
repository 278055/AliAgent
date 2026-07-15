package com.macro.mall.service.impl;

import com.macro.mall.bo.AdminUserDetails;
import com.macro.mall.mapper.UmsAdminLoginLogMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsResource;
import com.macro.mall.model.UmsRole;
import com.macro.mall.security.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UmsAdminIdentityLoginTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UmsAdminLoginLogMapper loginLogMapper;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void staffLoginIssuesTrustedRoleAndPermissionClaims() {
        UmsAdmin admin = new UmsAdmin();
        admin.setId(7L);
        admin.setUsername("staff-login");
        admin.setPassword("encoded-password");
        admin.setStatus(1);
        UmsRole role = new UmsRole();
        role.setName("客服");
        UmsResource resource = new UmsResource();
        resource.setId(9L);
        resource.setName("订单查询");

        UmsAdminServiceImpl service = spy(new UmsAdminServiceImpl());
        JwtTokenUtil jwtTokenUtil = new JwtTokenUtil("test-signing-key", 300L, "Bearer ");
        ReflectionTestUtils.setField(service, "jwtTokenUtil", jwtTokenUtil);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "loginLogMapper", loginLogMapper);
        ReflectionTestUtils.setField(service, "tenantId", "dev-tenant");
        doReturn(new AdminUserDetails(admin, Arrays.asList(resource))).when(service).loadUserByUsername("staff-login");
        doReturn(admin).when(service).getAdminByUsername("staff-login");
        doReturn(Arrays.asList(role)).when(service).getRoleList(7L);
        doReturn(Arrays.asList(resource)).when(service).getResourceList(7L);
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        String token = service.login("staff-login", "password");
        Claims claims = jwtTokenUtil.parseAndValidate(token);

        assertNotNull(token);
        assertEquals("7", claims.getSubject());
        assertEquals("STAFF", claims.get("subjectType", String.class));
        assertEquals("dev-tenant", claims.get("tenantId", String.class));
        assertEquals(Arrays.asList("客服"), claims.get("roles", java.util.List.class));
        assertEquals(Arrays.asList("9:订单查询"), claims.get("permissions", java.util.List.class));
    }
}
