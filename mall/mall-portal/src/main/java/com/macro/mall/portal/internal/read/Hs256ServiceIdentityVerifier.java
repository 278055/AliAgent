package com.macro.mall.portal.internal.read;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.List;

public class Hs256ServiceIdentityVerifier implements ServiceIdentityVerifier {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final long MAX_TOKEN_LIFETIME_MILLIS = 300_000L;

    private final String secret;

    public Hs256ServiceIdentityVerifier(String secret) {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("SERVICE_JWT_SECRET must be at least 32 bytes");
        }
        this.secret = secret;
    }

    @Override
    public void verify(String authorization, String audience, String requiredScope) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new InternalAuthenticationException("missing service authorization");
        }
        try {
            Jws<Claims> signedClaims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(authorization.substring(BEARER_PREFIX.length()));
            if (!SignatureAlgorithm.HS256.getValue().equals(signedClaims.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("service JWT algorithm is invalid");
            }
            validateClaims(signedClaims.getBody(), audience, requiredScope);
        } catch (InternalAuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InternalAuthenticationException("invalid service authorization");
        }
    }

    private void validateClaims(Claims claims, String audience, String requiredScope) {
        String caller = claims.get("caller", String.class);
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        List<?> scopes = claims.get("scopes", List.class);
        if (isBlank(claims.getSubject()) || isBlank(caller) || !caller.equals(claims.getSubject())
                || !hasAudience(claims, audience)
                || scopes == null || !scopes.contains(requiredScope) || issuedAt == null || expiration == null
                || isBlank(claims.getId()) || expiration.before(issuedAt)
                || expiration.getTime() - issuedAt.getTime() > MAX_TOKEN_LIFETIME_MILLIS) {
            throw new InternalAuthenticationException("service JWT claims are invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean hasAudience(Claims claims, String audience) {
        Object claim = claims.get("aud");
        if (claim instanceof String) {
            return audience.equals(claim);
        }
        return claim instanceof List && ((List<?>) claim).contains(audience);
    }
}
