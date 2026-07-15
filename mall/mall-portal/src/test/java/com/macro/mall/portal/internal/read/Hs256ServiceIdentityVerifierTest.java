package com.macro.mall.portal.internal.read;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Hs256ServiceIdentityVerifierTest {
    private static final String SECRET = "test-service-jwt-secret-must-be-at-least-32-bytes";
    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    private final Hs256ServiceIdentityVerifier verifier = new Hs256ServiceIdentityVerifier(SECRET);

    @Test
    void acceptsValidMallInternalReadToken() {
        assertDoesNotThrow(() -> verifier.verify(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "mall",
                Arrays.asList("mall.internal.read"), Instant.now(), Instant.now().plusSeconds(300), true),
                "mall", "mall.internal.read"));
    }

    @Test
    void acceptsMallAudienceArrayIssuedByPlatformServiceSecurity() {
        assertDoesNotThrow(() -> verifier.verify(AUTHORIZATION_PREFIX + tokenWithAudienceArray(),
                "mall", "mall.internal.read"));
    }

    @Test
    void rejectsMissingBearerPrefix() {
        assertRejected(token(SECRET, "gateway-service", "mall", Arrays.asList("mall.internal.read"),
                Instant.now(), Instant.now().plusSeconds(300), true));
    }

    @Test
    void rejectsWrongSignature() {
        assertRejected(AUTHORIZATION_PREFIX + token("different-test-service-jwt-secret-at-least-32-bytes",
                "gateway-service", "mall", Arrays.asList("mall.internal.read"), Instant.now(),
                Instant.now().plusSeconds(300), true));
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "mall", Arrays.asList("mall.internal.read"),
                now.minusSeconds(301), now.minusSeconds(1), true));
    }

    @Test
    void rejectsWrongAudience() {
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "other-service",
                Arrays.asList("mall.internal.read"), Instant.now(), Instant.now().plusSeconds(300), true));
    }

    @Test
    void rejectsMissingRequiredScope() {
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "mall",
                Arrays.asList("other.scope"), Instant.now(), Instant.now().plusSeconds(300), true));
    }

    @Test
    void rejectsTokenWithoutCaller() {
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, null, "mall", Arrays.asList("mall.internal.read"),
                Instant.now(), Instant.now().plusSeconds(300), true));
    }

    @Test
    void rejectsTokenWithoutJti() {
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "mall",
                Arrays.asList("mall.internal.read"), Instant.now(), Instant.now().plusSeconds(300), false));
    }

    @Test
    void rejectsTokenWithoutIssuedAt() {
        assertRejected(AUTHORIZATION_PREFIX + tokenWithoutTimeClaim(false));
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        assertRejected(AUTHORIZATION_PREFIX + tokenWithoutTimeClaim(true));
    }

    @Test
    void rejectsTokenValidForMoreThanFiveMinutes() {
        assertRejected(AUTHORIZATION_PREFIX + token(SECRET, "gateway-service", "mall",
                Arrays.asList("mall.internal.read"), Instant.now(), Instant.now().plusSeconds(301), true));
    }

    private void assertRejected(String authorization) {
        assertThrows(InternalAuthenticationException.class,
                () -> verifier.verify(authorization, "mall", "mall.internal.read"));
    }

    private String token(String secret, String caller, String audience, java.util.List<String> scopes,
                         Instant issuedAt, Instant expiresAt, boolean withJti) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setAudience(audience)
                .claim("scopes", scopes)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt));
        if (caller != null) {
            builder.setSubject(caller).claim("caller", caller);
        }
        if (withJti) {
            builder.setId(UUID.randomUUID().toString());
        }
        return builder.signWith(SignatureAlgorithm.HS256, secret).compact();
    }

    private String tokenWithAudienceArray() {
        Instant now = Instant.now();
        return Jwts.builder().setSubject("gateway-service").claim("caller", "gateway-service")
                .claim("aud", Arrays.asList("mall")).claim("scopes", Arrays.asList("mall.internal.read"))
                .setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(300)))
                .setId(UUID.randomUUID().toString()).signWith(SignatureAlgorithm.HS256, SECRET).compact();
    }

    private String tokenWithoutTimeClaim(boolean omitExpiration) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder().setSubject("gateway-service")
                .claim("caller", "gateway-service").setAudience("mall")
                .claim("scopes", Arrays.asList("mall.internal.read")).setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (omitExpiration) {
            builder.setIssuedAt(Date.from(now));
        } else {
            builder.setExpiration(Date.from(now.plusSeconds(300)));
        }
        return builder.signWith(SignatureAlgorithm.HS256, SECRET).compact();
    }
}
