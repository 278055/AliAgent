package com.macro.mall.security.util;

import com.macro.mall.security.identity.IdentityTokenClaims;
import com.macro.mall.security.identity.IdentityTokenException;
import com.macro.mall.security.identity.IdentityContext;
import com.macro.mall.security.identity.SubjectType;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenUtilTest {

    private final JwtTokenUtil tokenUtil = new JwtTokenUtil("test-signing-key", 300L, "Bearer ");

    @Test
    void issuesMemberTokenWithRequiredTrustedClaims() {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "member-42", "member-login", SubjectType.MEMBER, "dev-tenant",
                Arrays.asList("MEMBER"), Arrays.asList("mall:portal:access")));

        Claims claims = tokenUtil.parseAndValidate(token);

        assertEquals("member-42", claims.getSubject());
        assertEquals("MEMBER", claims.get("subjectType", String.class));
        assertEquals("dev-tenant", claims.get("tenantId", String.class));
        assertEquals(Arrays.asList("MEMBER"), claims.get("roles", java.util.List.class));
        assertEquals(Arrays.asList("mall:portal:access"), claims.get("permissions", java.util.List.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getId());
        assertFalse(claims.containsKey("password"));
    }

    @Test
    void issuesStaffTokenWithRoleAndPermissionSnapshot() {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "staff-7", "staff-login", SubjectType.STAFF, "dev-tenant",
                Arrays.asList("运营", "客服"), Arrays.asList("1:商品管理", "2:订单管理")));

        Claims claims = tokenUtil.parseAndValidate(token);

        assertEquals("STAFF", claims.get("subjectType", String.class));
        assertEquals(Arrays.asList("运营", "客服"), claims.get("roles", java.util.List.class));
        assertEquals(Arrays.asList("1:商品管理", "2:订单管理"), claims.get("permissions", java.util.List.class));
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenUtil expiredTokenUtil = new JwtTokenUtil("test-signing-key", -1L, "Bearer ");
        String token = expiredTokenUtil.generateToken(IdentityTokenClaims.of(
                "member-42", "member-login", SubjectType.MEMBER, "dev-tenant",
                Arrays.asList("MEMBER"), Arrays.asList("mall:portal:access")));

        assertThrows(IdentityTokenException.class, () -> tokenUtil.parseAndValidate(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "member-42", "member-login", SubjectType.MEMBER, "dev-tenant",
                Arrays.asList("MEMBER"), Arrays.asList("mall:portal:access")));
        String tamperedToken = token.substring(0, token.length() - 1) + "x";

        assertThrows(IdentityTokenException.class, () -> tokenUtil.parseAndValidate(tamperedToken));
    }

    @Test
    void exposesTrustedTenantContextOnlyAfterTokenValidation() {
        String token = tokenUtil.generateToken(IdentityTokenClaims.of(
                "staff-7", "staff-login", SubjectType.STAFF, "dev-tenant",
                Arrays.asList("运营"), Arrays.asList("1:商品管理")));

        IdentityContext context = tokenUtil.getIdentityContext(token);

        assertEquals("staff-7", context.getSubjectId());
        assertEquals(SubjectType.STAFF, context.getSubjectType());
        assertEquals("dev-tenant", context.getTenantId());
    }
}
