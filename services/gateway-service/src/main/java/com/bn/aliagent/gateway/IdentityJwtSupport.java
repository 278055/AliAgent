package com.bn.aliagent.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class IdentityJwtSupport {
    private final byte[] secret;

    IdentityJwtSupport(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("IDENTITY_JWT_SECRET must be at least 32 bytes");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    TrustedIdentity verify(String token) {
        Claims claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret)).build().parseSignedClaims(token).getPayload();
        String subjectType = required(claims.get("subjectType", String.class));
        if (!"MEMBER".equals(subjectType) && !"STAFF".equals(subjectType)) {
            throw new IllegalArgumentException("Unsupported subject type");
        }
        return new TrustedIdentity(required(claims.get("tenantId", String.class)), required(claims.getSubject()),
                subjectType, strings(claims.get("roles")), strings(claims.get("permissions")));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("Missing identity claims");
        return values.stream().map(String::valueOf).toList();
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing identity claim");
        return value;
    }
}
