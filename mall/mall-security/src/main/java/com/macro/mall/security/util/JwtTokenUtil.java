package com.macro.mall.security.util;

import cn.hutool.core.util.StrUtil;
import com.macro.mall.security.identity.IdentityTokenClaims;
import com.macro.mall.security.identity.IdentityContext;
import com.macro.mall.security.identity.IdentityTokenException;
import com.macro.mall.security.identity.SubjectType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JwtToken生成的工具类
 * JWT token的格式：header.payload.signature
 * header的格式（算法、token的类型）：
 * {"alg": "HS512","typ": "JWT"}
 * payload的格式（用户名、创建时间、生成时间）：
 * {"sub":"wang","created":1489079981393,"exp":1489684781}
 * signature的生成算法：
 * HMACSHA512(base64UrlEncode(header) + "." +base64UrlEncode(payload),secret)
 * Created by macro on 2018/4/26.
 */
public class JwtTokenUtil {
    private static final String CLAIM_KEY_LOGIN_NAME = "loginName";
    private static final String CLAIM_KEY_SUBJECT_TYPE = "subjectType";
    private static final String CLAIM_KEY_TENANT_ID = "tenantId";
    private static final String CLAIM_KEY_ROLES = "roles";
    private static final String CLAIM_KEY_PERMISSIONS = "permissions";
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    public JwtTokenUtil() {
    }

    public JwtTokenUtil(String secret, Long expiration, String tokenHead) {
        this.secret = secret;
        this.expiration = expiration;
        this.tokenHead = tokenHead;
    }

    /**
     * 根据负责生成JWT的token
     */
    private String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setId(UUID.randomUUID().toString())
                .setExpiration(generateExpirationDate())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    /**
     * 从token中获取JWT中的负载
     */
    public Claims parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
            validateRequiredClaims(claims);
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new IdentityTokenException("Invalid identity token", e);
        }
    }

    private void validateRequiredClaims(Claims claims) {
        if (isBlank(claims.getSubject()) || isBlank(claims.get(CLAIM_KEY_LOGIN_NAME, String.class))
                || isBlank(claims.get(CLAIM_KEY_SUBJECT_TYPE, String.class))
                || isBlank(claims.get(CLAIM_KEY_TENANT_ID, String.class))
                || claims.get(CLAIM_KEY_ROLES) == null || claims.get(CLAIM_KEY_PERMISSIONS) == null
                || claims.getIssuedAt() == null || claims.getExpiration() == null || isBlank(claims.getId())) {
            throw new IdentityTokenException("Identity token misses required claims");
        }
        try {
            SubjectType.valueOf(claims.get(CLAIM_KEY_SUBJECT_TYPE, String.class));
        } catch (IllegalArgumentException e) {
            throw new IdentityTokenException("Identity token has an invalid subject type", e);
        }
    }

    /**
     * 生成token的过期时间
     */
    private Date generateExpirationDate() {
        return new Date(System.currentTimeMillis() + expiration * 1000);
    }

    /**
     * 从token中获取登录用户名
     */
    public String getUserNameFromToken(String token) {
        try {
            return parseAndValidate(token).get(CLAIM_KEY_LOGIN_NAME, String.class);
        } catch (IdentityTokenException e) {
            return null;
        }
    }

    /**
     * 验证token是否还有效
     *
     * @param token       客户端传入的token
     * @param userDetails 从数据库中查询出来的用户信息
     */
    public boolean validateToken(String token, String username) {
        return username.equals(getUserNameFromToken(token));
    }

    public IdentityContext getIdentityContext(String token) {
        return IdentityContext.from(parseAndValidate(token));
    }

    public String generateToken(IdentityTokenClaims identity) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Claims.SUBJECT, identity.getSubjectId());
        claims.put(CLAIM_KEY_LOGIN_NAME, identity.getLoginName());
        claims.put(CLAIM_KEY_SUBJECT_TYPE, identity.getSubjectType().name());
        claims.put(CLAIM_KEY_TENANT_ID, identity.getTenantId());
        claims.put(CLAIM_KEY_ROLES, identity.getRoles());
        claims.put(CLAIM_KEY_PERMISSIONS, identity.getPermissions());
        return generateToken(claims);
    }

    /**
     * 当原来的token没过期时是可以刷新的
     *
     * @param oldToken 带tokenHead的token
     */
    public String refreshHeadToken(String oldToken) {
        if(StrUtil.isEmpty(oldToken)){
            return null;
        }
        String token = oldToken.substring(tokenHead.length());
        if(StrUtil.isEmpty(token)){
            return null;
        }
        Claims claims;
        try {
            claims = parseAndValidate(token);
        } catch (IdentityTokenException e) {
            return null;
        }
        return generateToken(IdentityTokenClaims.of(
                claims.getSubject(), claims.get(CLAIM_KEY_LOGIN_NAME, String.class),
                com.macro.mall.security.identity.SubjectType.valueOf(claims.get(CLAIM_KEY_SUBJECT_TYPE, String.class)),
                claims.get(CLAIM_KEY_TENANT_ID, String.class),
                claims.get(CLAIM_KEY_ROLES, List.class), claims.get(CLAIM_KEY_PERMISSIONS, List.class)));
    }

    /**
     * 判断token在指定时间内是否刚刚刷新过
     * @param token 原token
     * @param time 指定时间（秒）
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
