package com.bn.aliagent.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityJwtSupportTest {
    @Test
    void verifiedClaimsAreTheOnlyTrustedIdentitySource() {
        String secret = "test-identity-jwt-secret-must-be-at-least-32-bytes";
        Instant now = Instant.now();
        String token = Jwts.builder().subject("member-1").claim("loginName", "member")
                .claim("subjectType", "MEMBER").claim("tenantId", "test-p4-tenant")
                .claim("roles", List.of("MEMBER")).claim("permissions", List.of("conversation:write"))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).id("test-jti")
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();

        TrustedIdentity identity = new IdentityJwtSupport(secret).verify(token);

        assertEquals("test-p4-tenant", identity.tenantId());
        assertEquals("member-1", identity.subjectId());
        assertEquals("MEMBER", identity.subjectType());
        assertEquals(List.of("MEMBER"), identity.roles());
        assertEquals(List.of("conversation:write"), identity.permissions());
    }
}
