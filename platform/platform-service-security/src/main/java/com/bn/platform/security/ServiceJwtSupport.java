package com.bn.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;

public final class ServiceJwtSupport {
    private final SecretKey key;

    public ServiceJwtSupport(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("SERVICE_JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String caller, String audience, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder().subject(caller).claim("caller", caller).audience().add(audience).and()
                .claim("scopes", scopes).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
                .id(UUID.randomUUID().toString()).signWith(key).compact();
    }

    public void verify(String token, String audience, String scope) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!claims.getAudience().contains(audience) || !claims.get("scopes", List.class).contains(scope)) {
            throw new IllegalArgumentException("Service JWT audience or scope is invalid");
        }
    }
}
